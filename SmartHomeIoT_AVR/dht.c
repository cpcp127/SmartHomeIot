#include <avr/io.h>
#include <util/delay.h> 
#define DHT11_PIN PD7
uint8_t c = 0;

void Request()				
{
	DDRD |= (1<<DHT11_PIN);
	PORTD &= ~(1<<DHT11_PIN);
	_delay_ms(20);			
	PORTD |= (1<<DHT11_PIN);
	_delay_us(40);
}

void Response()				
{
	DDRD &= ~(1<<DHT11_PIN);
	while(PIND & (1<<DHT11_PIN));
	while((PIND & (1<<DHT11_PIN))==0);
	while(PIND & (1<<DHT11_PIN));
}

uint8_t Receive_data()			
{	
	for (int q=0; q<8; q++)
	{
		while((PIND & (1<<DHT11_PIN)) == 0);  	/* check received bit 0 or 1 */
		_delay_us(30);
		if(PIND & (1<<DHT11_PIN)) 				/* if high pulse is greater than 30ms */
		{
			c = (c<<1)|(0x01);					/* then its logic HIGH */
		}
		else									/* otherwise its logic LOW */
		{
			c = (c<<1);
		}

		while(PIND & (1<<DHT11_PIN));
		
	}
	return c;
}
