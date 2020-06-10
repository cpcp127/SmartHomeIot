#include <avr/io.h>
#include <stdlib.h>
#include <stdio.h>
#include <avr/interrupt.h> 
#include <util/delay.h> 
#include <string.h>
#include "lcd.h"

// 서보모터 사용
static volatile unsigned char Pos_CMD = 0 ;    	// 서보 위치 명령 ( 범위 : 0 - 180,  단위:  도 )
void Servo_On();							  	// 전등 스위치 On
void Servo_Off();								// 전등 스위치 Off
void Servo_SET();								// 전등 스위치 중간으로 

// 온습도 센서
void getDHT();									// 센서 데이터 수집
char i_rh[5], d_rh[5], i_temp[5], d_temp[5];	// 습도, 온도 변수(8비트 + '.' 8비트)
uint8_t I_RH,D_RH,I_Temp,D_Temp,CheckSum;		// 8비트 변수

// 미세먼지 센서
static char dust_count = 0;						
uint8_t dust[32] = {0};							// 32개의 데이터를 받을 배열
uint16_t pm10 ;									// 16비트 미세먼지 값
uint16_t pm10_old = 0;							// 값이 튀는것을 방지하기 위한 직전 값
char pm[3] = {0};								// 미세먼지 값 저장 배열

// 블루투스 통신
void init_serial(void) ;  						// Serial 통신포트 설정(USART0, USART1)
void SerialPutChar(char ch);					// 한글자 전송
void SerialPutString(char str[]);				// 문자열 전송
void sendData();								// 센서 값 전송

static volatile char recv_cnt = 0, rdata = 0, new_recv_flag = 0 ;  
static volatile char recv_data[3] = {0,0,0}; 
static volatile unsigned char Command_Error_Flag = 0 ; 

static volatile char Cmd_Message_1[] = {"on" }; // Motor On  명령어
static volatile char Cmd_Message_2[] = {"off"}; // Motor Off 명령어

// 범용 
void Display_Number_LCD( unsigned int num, unsigned char digit ) ;  // 부호없는 정수형 변수를 10진수 형태로 LCD 에 디스플레이 
void HexToDec( unsigned short num, unsigned short radix); 
char NumToAsc( unsigned char Num ); 
void msec_delay(unsigned int n);
void usec_delay(unsigned int n);

void pin_init();													// 핀 설정 설정
void init();														// Interrupt , Timer, Register 설정

static volatile unsigned char cnumber[5] = {0, 0, 0, 0, 0}; 

int main() 
{   
	char eq_count1=0, eq_count2=0, cmd_data = 0xFF ;  	  
    unsigned char i=0 ;
	
	pin_init();		  	// 핀 설정
	init();			  	// Interrupt , Timer, Register 설정
	init_serial() ;   	// Serial 통신포트 설정(USART0, USART1)
	Servo_SET();		// 모터 중앙에 위치
	while (1) 
	{ 
		if( new_recv_flag == 1)
		{
			for( i=0; i < recv_cnt ; i++) 
			{
				if( recv_data[i] == Cmd_Message_1[i] ) eq_count1++ ;
			    if( recv_data[i] == Cmd_Message_2[i] ) eq_count2++ ; 
            }
	
			if(eq_count1 == 2) cmd_data = 1;
			else if(eq_count2 == 3) cmd_data = 2;
			else cmd_data = 0xFE;
			
			eq_count1 = 0, eq_count2 = 0 , new_recv_flag = 0;
		}
		if(cmd_data == 1)
		{
			Servo_On();
			msec_delay(100);
			LcdCommand(ALLCLR);
			LcdMove(0,0);
			LcdPuts("Turn On!!");
		} // On 명령 들어왔을 때
		else if(cmd_data == 2)
		{
			Servo_Off();
			msec_delay(100);
			LcdCommand(ALLCLR);
			LcdMove(0,0);
			LcdPuts("Turn Off!!");

		} // Off 명령 들어왔을 때
		else if(cmd_data == 0xFE)
		{
			LcdCommand(ALLCLR);
			LcdMove(0,0);
			LcdPuts("Command Error!!");
			LcdMove(1,0);
			LcdPuts(recv_data);
		}
		Servo_SET();
		cmd_data = 0xFF;
	}
} 

ISR(TIMER0_OVF_vect)   // Timer0 overflow interrupt (10 msec)  service routine
{

	static unsigned short  time_index = 0, send_time_index = 0;

    TCNT0 = 256 - 156;       	// 내부클럭주기 = 1024/ (16x10^6) = 64 usec,  
                             	// 오버플로인터럽트 주기 = 10msec
                             	// 156 = 10msec/ 64usec

    time_index++ ; 

    if( time_index == 200 )		// 샘플링주기 10msec * 200 = 2sec
    {       	
	   	getDHT();				// DHT 센서 데이터 수집
	   	sendData();				// 수집된 데이터 전송
		
		time_index = 0; 
   }
}

ISR( USART0_RX_vect)
{
	dust[dust_count] = UDR0;
	dust_count ++;
	static unsigned char first = 0;
	if(dust_count >= 32)
	{
		if((dust[0] == 0x42)&&(dust[1] == 0x4d))
		{
			pm10 = (dust[14] << 8) + (dust[15]);
			
			if(first == 0)
			{
				first = 1;
				pm10_old = pm10;
			}
			else
			{
				if((pm10 - pm10_old) > 20)
				{
					pm10 = pm10_old;
				}
				else
				{
					pm10_old = pm10;
				}
			} // 값이 튀는것을 방지
			itoa(pm10,pm,10);

			LcdMove(1,5);
			LcdPuts(pm);
		}
		dust_count = 0 ;		
	}
} // PMS7003 센서 데이터 수집 인터럽트 

ISR( USART1_RX_vect )
{
    static unsigned char r_cnt = 0 ;

    rdata = UDR1; 

    if( rdata != '.' )                      // 수신된 데이터가 마지막 문자를 나타내는 데이터(마침표)가 아니면
    {
        recv_data[r_cnt] = rdata;        //  수신된 문자 저장 
	    r_cnt++;                         //  수신 문자 갯수 증가 

		new_recv_flag = 0;
    }
    else if(  rdata == '.' )                // 수신된데이터가 마지막 문자를 나타내는 데이터(마침표) 이면
    {
        recv_cnt = r_cnt ;                  // 수신된 데이터 바이트수 저장
        r_cnt = 0;  
        
		new_recv_flag = 1;
    }
} // 블루투스 수신 인터럽트 

void pin_init()
{
	DDRB |= 0x80;    //  PWM 포트: OC2( PB7 ) 출력설정
}
void init()
{
	LcdInit();

	LcdCommand(ALLCLR);
	LcdMove(0,0);  
	LcdPuts("HM=");
	LcdMove(0,8); 
	LcdPuts("TP= ");
	LcdMove(1,0);
	LcdPuts("Dust=");

	TCCR0 = 0x00; 
    TCNT0 = 256 - 100;      // 내부클럭주기 = 8/ (16x10^6) = 0.5 usec,  
                            // 오버플로인터럽트 주기 = 50usec
                            // 156 = 50usec/ 0.5use

	TIMSK = 0x01;  			// Timer0 overflow interrupt enable 
	
	TCCR0 |= 0x07; 			// Clock Prescaler N=1024 (Timer 0 Start)

	// Timer0 Overflow Interrupt 설정 



	TCCR2 |= 0x68;   		// Trigger signal (OC2)   발생 :  WGM20(bit6)=1,  WGM21(bit3)=1,  COM21(bit5)=1, COM20(bit4)=0 ,  
	TCCR2 |= 0x05;   		// 1024분주,  내부클럭주기 = 64usec  : CS22(bit2) = 1, CS21(bit1) = 0,  CS20(bit0) = 1 

	Pos_CMD = 90 ;
    OCR2 = ( 135 * Pos_CMD )/900 + 10 ; 
	
	// Motor PWM 설정 

	sei();         // Global Interrupt Enable 
}
void init_serial(void)
{
    UCSR0A = 0x00;          // 초기화
    UCSR0B = 0x18;          // 송수신허용,  송수신 인터럽트 금지
    UCSR0C = 0x06;          // 데이터 전송비트 수 8비트로 설정.
    
    UBRR0H = 0x00;
    UBRR0L = 103;           // Baud Rate 9600 

	UCSR0B |= 0x80;   		// UART0 수신(RX) 완료 인터럽트 허용 PMS7003 데이터 수집

	UCSR1A = 0x00;          // 초기화
    UCSR1B = 0x18;          // 송수신허용,  송수신 인터럽트 금지
    UCSR1C = 0x06;			// 데이터 전송비트 수 8비트로 설정.
    
    UBRR1H = 0x00;
    UBRR1L = 103;			// Baud Rate 9600 

	UCSR1B |= 0x80;   		// UART1 수신(RX) 완료 인터럽트 허용 블루투스 통신
}

void sendData()
{
	SerialPutString(i_temp);
	SerialPutChar('.');
	SerialPutString(d_temp);
	SerialPutChar(',');
	SerialPutString(i_rh);
	SerialPutChar('.');
	SerialPutString(d_rh);
	SerialPutChar(',');
	SerialPutString(pm);
	SerialPutChar('/');
}

void getDHT()
{
	Request();					// 데이터 전송 요청
	Response();					// 요청 대기
	I_RH=Receive_data();		// 습도 상위 8비트 전송
	D_RH=Receive_data();		// 습도 하위 8비트 전송
	I_Temp=Receive_data();		// 온도 상위 8비트 전송
	D_Temp=Receive_data();		// 온도 하위 8비트 전송
	CheckSum=Receive_data();	// CheckSum 비트 = 습도 16비트 + 온도 16비트 
	
	if ((I_RH + D_RH + I_Temp + D_Temp) != CheckSum)
	{
		LcdMove(0,0);
		LcdPuts("Error");
	}
		
	else
	{	
		LcdCommand(ALLCLR);
		LcdMove(0,0);  
		LcdPuts("HM=");
		LcdMove(0,8); 
		LcdPuts("TP= ");
		LcdMove(1,0);
		LcdPuts("Dust=");
		LcdMove(1,5);
		LcdPuts(pm);

		itoa(I_RH,i_rh,10);
		LcdMove(0,3);
		LcdPuts(i_rh);
		LcdMove(0,5);
		LcdPuts(".");
			
		itoa(D_RH,d_rh,10);
		LcdMove(0,6);
		LcdPuts(d_rh);
		LcdMove(0,7);
		LcdPuts("%");

		// 습도 Display

		itoa(I_Temp,i_temp,10);
		LcdMove(0,11);
		LcdPuts(i_temp);
		LcdMove(0,13);
		LcdPuts(".");
			
		itoa(D_Temp,d_temp,10);
		LcdMove(0,14);
		LcdPuts(d_temp);
		LcdMove(0,15);
		LcdPuts("C");
		
		// 온도 Display
	}
				
	msec_delay(10);
}

void Servo_On()
{
	Pos_CMD = 150 ;   		                 // 서보 위치 명령 =  0 도 (왼쪽 끝)  
    OCR2 = ( 135 * Pos_CMD )/900  + 10  ;   
}
void Servo_Off()
{
	Pos_CMD = 30 ;   		                 // 서보 위치 명령 =  180 도 (오른쪽 끝)  
    OCR2 = ( 135 * Pos_CMD )/900  + 10  ;   
}
void Servo_SET()
{
	Pos_CMD = 90 ;   		                 // 서보 위치 명령 =  90 도 (오른쪽 끝)  
    OCR2 = ( 135 * Pos_CMD )/900  + 10  ;   
}

void SerialPutChar(char ch)
{
	while(!(UCSR1A & (1<<UDRE)));			// 버퍼가 빌 때를 기다림
  	UDR1 = ch;								// 버퍼에 문자를 쓴다
} // 한 문자를 송신한다.

void SerialPutString(char *str)
{
    while(*str != '\0')          			// 수신된 문자가 Null 문자( 0x00 )가 아니면 
    {
        SerialPutChar(*str++);
    }
} // 문자열을 송신한다.

void Display_Number_LCD( unsigned int num, unsigned char digit )       // 부호없는 정수형 변수를 10진수 형태로 LCD 에 디스플레이 
{

	HexToDec( num, 10); //10진수로 변환 

	if( digit == 0 )     digit = 1 ;
	if( digit > 5 )      digit = 5 ;
 
    if( digit >= 5 )     LcdPutchar( NumToAsc(cnumber[4]) );  // 10000자리 디스플레이
	
	if( digit >= 4 )     LcdPutchar(NumToAsc(cnumber[3]));    // 1000자리 디스플레이 

	if( digit >= 3 )     LcdPutchar(NumToAsc(cnumber[2]));    // 100자리 디스플레이 

	if( digit >= 2 )     LcdPutchar(NumToAsc(cnumber[1]));    // 10자리 디스플레이

	if( digit >= 1 )     LcdPutchar(NumToAsc(cnumber[0]));    //  1자리 디스플레이

}


void Display_TMP_LCD( unsigned int tp  )       // 온도를 10진수 형태로 LCD 에 디스플레이 
{

	HexToDec( tp, 10); //10진수로 변환 

    LcdPutchar(NumToAsc(cnumber[2]) );   // 10자리 디스플레이
	
    LcdPutchar(NumToAsc(cnumber[1]));    // 1자리 디스플레이 

    LcdPuts( ".");                       // 소숫점(.) 디스플레이 

    LcdPutchar(NumToAsc(cnumber[0]));    // 0.1 자리 디스플레이 
}


void HexToDec( unsigned short num, unsigned short radix) 
{
	int j ;

	for(j=0; j<5 ; j++) cnumber[j] = 0 ;

	j=0;
	do
	{
		cnumber[j++] = num % radix ; 
		num /= radix; 

	} while(num);
} 

char NumToAsc( unsigned char Num )
{
	if( Num <10 ) Num += 0x30; 
	else          Num += 0x37; 

	return Num ;
}

void msec_delay(unsigned int n)
{	
	for(; n>0; n--)		// 1msec 시간 지연을 n회 반복
		_delay_ms(1);		// 1msec 시간 지연
}

void usec_delay(unsigned int n)
{	
	for(; n>0; n--)		// 1usec 시간 지연을 n회 반복
		_delay_us(1);		// 1usec 시간 지연
}
