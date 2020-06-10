package my.final_project;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {
    Context context;

    ImageView backgroundImage;
    TextView alarmText, lightBulbText, tempText, humText, dustText;
    Button alarmButton, turnOnButton, turnOffButton;
    CheckBox checkBox;
    FloatingActionButton fab;
    // View 객체

    NotificationManager alarm_notificationManager;      // 알람 푸쉬
    AlarmManager alarmManager;                          // 알람 매니저
    NotificationManager notificationManager;
    NotificationCompat.Builder builder;                 // 계속 사용되는 푸쉬

    Calendar calendar = Calendar.getInstance();         // 현재 시간을 확인하기 위한 캘린더 객체
    Calendar alarmCalendar = Calendar.getInstance();    // 알람 시간을 저장해주기 위한 캘린더 객체

    PendingIntent alarmPendingIntent;
    Intent alarmIntent;

    boolean checkboxChecked = false;                    // 체크 박스의 상태를 저장하는 변수
    int alarmHour ;                                     // 알람 시간(Hour : 24시간 단위)을 저장하는 변수
    int alarmMinute ;                                   // 일람 시간(Minute)을 저장하는 변수
    int timePickerMode = 0;                             // 타임 피커의 모드를 나타내는 변수
    public static int alarmMode = 0;                    // 알람 모드를 나타내주는 변수

    private final String TAG = "TEST+Main_activity";    // 디버깅을 위한 Log 태그

    public static final int MODE_REQUEST = 1 ;
    public static final int MESSAGE_WRITE = 2;
    private static final int STATE_SENDING = 1;
    private static final int STATE_NO_SENDING = 2;

    private int mSendingState ;
    public static BluetoothService bluetoothServiceMain = null;
    StringBuffer mOutStringBuffer = new StringBuffer("");
    String[] sensor;
    int dust;
    // 블루투스 사용

    private final Handler handler = new Handler() {
        public void handleMessage(Message message) {
            super.handleMessage(message);
            Log.d(TAG,"handle");
            switch (message.what) {
                case MESSAGE_WRITE : {
                    switch (message.arg1) {
                        case 1 : {
                            String tmp = message.obj.toString();
                            sensor = tmp.split(",");
                            tempText = findViewById(R.id.tempText);
                            humText = findViewById(R.id.humText);
                            tempText.setText(sensor[0] + " C");
                            tempText.setTextSize(30);
                            humText.setText(sensor[1] + " %");
                            humText.setTextSize(30);
                            sensor[2] = sensor[2].substring(0, sensor[2].length() -1);
                            dust = Integer.parseInt(sensor[2]);
                            if(dust >= 0 && dust <= 30) {
                                dustText.setText("좋음\n("+sensor[2]+"㎍/m³)");
                                dustText.setTextColor(Color.BLUE);
                            }
                            else if(dust>30 && dust<=80) {
                                dustText.setText("보통\n("+sensor[2]+"㎍/m³)");
                                dustText.setTextColor(Color.parseColor("#52E252"));
                            }
                            else if(dust>80 && dust<=150) {
                                dustText.setText("나쁨\n("+sensor[2]+"㎍/m³)");
                                dustText.setTextColor(Color.parseColor("#FFC81E"));
                            }
                            else  {
                                dustText.setText("매우나쁨\n("+sensor[2]+"㎍/m³)");
                                dustText.setTextColor(Color.parseColor("#EB0000"));
                            }

                            dustText.setTextSize(27);
                            break;
                        }
                    }
                    break;
                }

            }
        }
    };
    // 메세지를 받는 핸들러

    private synchronized void sendMessage( String message, int mode ) {

        if ( mSendingState == STATE_SENDING ) {
            try {
                wait() ;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mSendingState = STATE_SENDING ;

        if ( bluetoothServiceMain.getState() != BluetoothService.STATE_CONNECTED ) {
            mSendingState = STATE_NO_SENDING ;
            return ;
        }

        if ( message.length() > 0 ) {
            byte[] send = message.getBytes() ;
            bluetoothServiceMain.write(send, mode) ;

            mOutStringBuffer.setLength(0) ;
            Log.d(TAG,"send");
        }

        mSendingState = STATE_NO_SENDING ;
//        notify() ;
    }
    // 블루투스 통신을 이용해 메세지를 보내는 함수
    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("종료하시겠습니까?");
        builder.setPositiveButton("yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(1);       // 알람 Notification 취소
                InitialActivity.btService = null;
                if(alarmManager != null)                                                                    // 알람이 설정 되어 있었다면
                    alarm_Off();                                                                            // 알람 설정 해제
                System.exit(0);                                                                     // 프로세스 종료
            }
        });
        builder.setNegativeButton("no", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
    // 뒤로가기 키를 눌렀을 때 알림창 생성

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothServiceMain = InitialActivity.btService;
        bluetoothServiceMain.set(this, handler);
        // initialActivity의 블루투스 서비스를 가져오고 Handler 만 세팅

        init();                 // findViewById 초기화
        changedBackground();    // 시간에 따라 배경 사진 변경

        SharedPreferences sf = getSharedPreferences("checked",MODE_PRIVATE);
        checkboxChecked = sf.getBoolean("checked", false);
        sf = getSharedPreferences("alarmMode", MODE_PRIVATE);
        alarmMode = sf.getInt("alarmMode", 0);
        sf = getSharedPreferences("timepickerMode",MODE_PRIVATE);
        timePickerMode = sf.getInt("timepickerMode",0);
        sf = getSharedPreferences("alarmHour", MODE_PRIVATE);
        alarmHour = sf.getInt("alarmHour", 0);
        sf = getSharedPreferences("alarmMinute", MODE_PRIVATE);
        alarmMinute = sf.getInt("alarmMinute", 0);
        // 각 상태들을 저장하고 있는 SharedPreferences를 불러옴

        if (checkboxChecked == true) {
            checkBox.setChecked(true);
            alarmButton.setVisibility(View.VISIBLE);
            setAlarmButtonText();
            showAlarmNotify();
        } else if(checkboxChecked == false){
            checkBox.setChecked(false);
            alarmButton.setVisibility(View.INVISIBLE);
            alarmButton.setText("알람 시간 설정");
        }
        // 어플을 껐다 켰을 때 끄기 전 체크박스가 체크된 상태였는지 확인
        dustText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), WeatherActivity.class);
                intent.putExtra("dust", dust);
                startActivity(intent);
            }
        });
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(checkBox.isChecked()) {
                    alarmButton.setVisibility(View.VISIBLE);
                    checkboxChecked = true;
                    SharedPreferences sf = getSharedPreferences("checked", MODE_PRIVATE);
                    SharedPreferences.Editor editor = sf.edit();
                    editor.putBoolean("checked", true);
                    editor.commit();
                }
                else {
                    Toast.makeText(getApplicationContext(),"알람 설정이 초기화 되었습니다.", Toast.LENGTH_LONG).show();
                    alarmButton.setVisibility(View.INVISIBLE);
                    alarmButton.setText("알람 시간 설정");
                    checkboxChecked = false;
                    SharedPreferences sf = getSharedPreferences("checked", MODE_PRIVATE);
                    SharedPreferences.Editor editor = sf.edit();
                    editor.putBoolean("checked", false);
                    editor.commit();
                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(1);       // 알람 Notification 취소
                    if(alarmManager != null)                                                                    // 알람이 설정 되어 있었다면
                        alarm_Off();                                                                            // 알람 설정 해제
                }
            }
        });
        // 알람 설정 CheckBox

        alarmButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onClick(View v) {
                switch (timePickerMode) {
                    case 0: {
                        TimePickerDialog timePickerDialog = new TimePickerDialog(MainActivity.this ,TimePickerDialog.THEME_DEVICE_DEFAULT_LIGHT,new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                alarmHour = hourOfDay;
                                alarmMinute = minute;
                                setAlarm();
                            }
                        },alarmHour,alarmMinute,false);
                        timePickerDialog.show();
                        break;
                    } // 원 형태의 TimePickerDialog

                    case 1: {
                        TimePickerDialog timePickerDialog = new TimePickerDialog(MainActivity.this, android.R.style.Theme_Holo_Light_Dialog,new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                alarmHour = hourOfDay;
                                alarmMinute = minute;
                                setAlarm();
                            }
                        },alarmHour,alarmMinute,false);
                        timePickerDialog.show();
                        break;
                    } // Spinner 형태의 TimePickerDialog
                }
            }
        });
        // 알람 시간 설정 버튼

        turnOnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                alert.setTitle("알림");
                alert.setMessage("전등을 키시겠습니까?");
                alert.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(bluetoothServiceMain.getState()==BluetoothService.STATE_CONNECTED) {
                            Log.d(TAG,"send success");
                            sendMessage("on.", MODE_REQUEST); // Atmega128에 전등 키라는 명령 전송
                            Toast.makeText(getApplicationContext(),"turn on.", Toast.LENGTH_LONG).show();
                        }
                        else {
                            Log.e(TAG, "블루투스 연결 오류");
                        }
                    }
                });
                alert.setNegativeButton("no", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                AlertDialog alertDialog = alert.create();
                alertDialog.show();
            }
        });
        // 전등 On 버튼

        turnOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                alert.setTitle("알림");
                alert.setMessage("전등을 끄시겠습니까 ?");
                alert.setPositiveButton("yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getApplicationContext(),"turn off.", Toast.LENGTH_LONG).show();
                        Log.d(TAG,"send success");
                        sendMessage("off.", MODE_REQUEST); // Atmega128에 전등 끄라는 명령 전송
                    }
                });
                alert.setNegativeButton("no", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                AlertDialog alertDialog = alert.create();
                alertDialog.show();
            }
        });
        // 전등 Off 버튼

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String item [] = {"TimePickerMode 세팅", "AlarmMode 세팅"};
                final String item1 [] = {"Circle Mode", "Spinner Mode"};
                final String item2 [] = {"기본","전기고문","김수미","색소폰"};
                final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MainActivity.this);
                alertBuilder.setTitle("설정");
                alertBuilder.setItems(item, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0 : {
                                AlertDialog.Builder alertBuilder1 = new AlertDialog.Builder(MainActivity.this);
                                alertBuilder1.setTitle("TimePickerMode 세팅");
                                alertBuilder1.setItems(item1, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        switch (which) {
                                            case 0: {
                                                Toast.makeText(getApplicationContext(), "Circle Mode", Toast.LENGTH_LONG).show();
                                                timePickerMode = 0;
                                                SharedPreferences sf = getSharedPreferences("timepickerMode", MODE_PRIVATE);
                                                SharedPreferences.Editor editor = sf.edit();
                                                editor.putInt("timepickerMode", 0);
                                                editor.commit();
                                                break;
                                            }
                                            case 1: {
                                                Toast.makeText(getApplicationContext(), "Spinner Mode", Toast.LENGTH_LONG).show();
                                                timePickerMode = 1;
                                                SharedPreferences sf = getSharedPreferences("timepickerMode", MODE_PRIVATE);
                                                SharedPreferences.Editor editor = sf.edit();
                                                editor.putInt("timepickerMode", 1);
                                                editor.commit();
                                                break;
                                            }
                                        }
                                    }
                                });
                                AlertDialog alertDialog1 = alertBuilder1.create();
                                alertDialog1.show();
                                break;
                            }
                            case 1 : {
                                AlertDialog.Builder alertBuilder1 = new AlertDialog.Builder(MainActivity.this);
                                alertBuilder1.setTitle("AlarmMode 세팅");
                                alertBuilder1.setItems(item2, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        switch (which) {
                                            case 0: {
                                                Toast.makeText(getApplicationContext(), "기본 Mode", Toast.LENGTH_LONG).show();
                                                alarmMode = 0;
                                                SharedPreferences sf = getSharedPreferences("alarmMode", MODE_PRIVATE);
                                                SharedPreferences.Editor editor = sf.edit();
                                                editor.putInt("alarmMode", 0);
                                                editor.commit();
                                                break;
                                            }
                                            case 1: {
                                                Toast.makeText(getApplicationContext(), "전기고문 Mode", Toast.LENGTH_LONG).show();
                                                alarmMode = 1;
                                                SharedPreferences sf = getSharedPreferences("alarmMode", MODE_PRIVATE);
                                                SharedPreferences.Editor editor = sf.edit();
                                                editor.putInt("alarmMode", 1);
                                                editor.commit();
                                                break;
                                            }
                                            case 2: {
                                                Toast.makeText(getApplicationContext(), "김수미 Mode", Toast.LENGTH_LONG).show();
                                                alarmMode = 2;
                                                SharedPreferences sf = getSharedPreferences("alarmMode", MODE_PRIVATE);
                                                SharedPreferences.Editor editor = sf.edit();
                                                editor.putInt("alarmMode", 2);
                                                editor.commit();
                                                break;
                                            }
                                            case 3:{
                                                Toast.makeText(getApplicationContext(), "색소폰 Mode", Toast.LENGTH_LONG).show();
                                                alarmMode = 3;
                                                SharedPreferences sf = getSharedPreferences("alarmMode", MODE_PRIVATE);
                                                SharedPreferences.Editor editor = sf.edit();
                                                editor.putInt("alarmMode",3);
                                                editor.commit();
                                            }
                                        }

                                    }
                                });
                                AlertDialog alertDialog1 = alertBuilder1.create();
                                alertDialog1.show();
                                break;
                            }
                        }
                    }
                });
                AlertDialog alertDialog = alertBuilder.create();
                alertDialog.show();

            }
        });

    }
    public void init() {
        checkBox = (CheckBox)findViewById(R.id.checkBox);
        alarmButton = (Button)findViewById(R.id.setAlarmTimeButton);
        turnOnButton = (Button)findViewById(R.id.turnOnButton);
        turnOffButton = (Button)findViewById(R.id.turnOffButton);
        fab = (FloatingActionButton)findViewById(R.id.fab);
        backgroundImage = (ImageView)findViewById(R.id.background);
        alarmText = (TextView)findViewById(R.id.alarmText);
        lightBulbText = (TextView)findViewById(R.id.lightBulbText);
        dustText = (TextView)findViewById(R.id.dustText);
    }
    // findViewById 초기화
    public void changedBackground() {
        int hour;
        hour = calendar.get(Calendar.HOUR_OF_DAY);
        Log.d(TAG, "hour = " + hour);
        if(hour>6 && hour<18)
        {
            backgroundImage.setImageResource(R.drawable.back_sky);
            alarmText.setTextColor(Color.BLACK);
            lightBulbText.setTextColor(Color.BLACK);
        }
        else if((hour>= 18 && hour <= 24 ) || (hour >= 0 && hour <=6))
        {
            backgroundImage.setImageResource(R.drawable.back_night);
            alarmText.setTextColor(Color.WHITE);
            lightBulbText.setTextColor(Color.WHITE);
        }
    }
    // 시간에 따라 배경 사진 변경

    public void setAlarmButtonText() {
        if(alarmHour > 0 && alarmHour < 12)
            alarmButton.setText("오전 " + alarmHour + "시 " + alarmMinute + "분");
        else if(alarmHour == 12)
            alarmButton.setText("오후 " + alarmHour + "시 " + alarmMinute + "분");
        else if(alarmHour > 12 && alarmHour <24)
            alarmButton.setText("오후 " + (alarmHour-12) + "시 " + alarmMinute + "분");
        else if(alarmHour == 0)
            alarmButton.setText("오전 " + "0시 " + alarmMinute + "분");
    }
    // 알람 설정 버튼의 텍스트 설정
    public void setAlarm() {
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmCalendar.set(Calendar.HOUR_OF_DAY, alarmHour);
        alarmCalendar.set(Calendar.MINUTE, alarmMinute);
        alarmCalendar.set(Calendar.SECOND,0);
        Toast.makeText(getApplicationContext(), "알람 시간 = " + alarmHour + "시 " + alarmMinute + "분입니다." ,Toast.LENGTH_LONG).show();
        setAlarmButtonText();
        showAlarmNotify();
        alarm_On();
        SharedPreferences sf = getSharedPreferences("alarmHour", MODE_PRIVATE);
        SharedPreferences.Editor editor = sf.edit();
        editor.putInt("alarmHour", alarmHour);
        SharedPreferences sf1 = getSharedPreferences("alarmMinute", MODE_PRIVATE);
        SharedPreferences.Editor editor1 = sf1.edit();
        editor1.putInt("alarmMinute", alarmMinute);
        editor.commit();
        editor1.commit();
        Log.d(TAG, "alarmHour " + alarmHour +"alarmMinute " +alarmMinute + "alarmSecond " + alarmCalendar.get(Calendar.SECOND));
    }
    public void alarm_On() {
        // int delay_time = 5;     // 알람이 몇 초 지연되서 울릴 때 딜레이 변수 설정
        this.context = this;
        Log.d(TAG, "setAlarm");
        int interval = 1000 * 60 * 60 * 24 ;
        // 설정된 알람 시간이 현재 시간보다 작을 경우 다음날 알람으로 적용해줘야 하는데 필요한 변수
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);
        alarmPendingIntent = PendingIntent.getBroadcast(MainActivity.this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if(Build.VERSION.SDK_INT >= 23) {
            if (alarmHour > calendar.get(Calendar.HOUR_OF_DAY)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), alarmPendingIntent);
            } // 세팅한 알람 시간이 현재 시간보다 클 경우
            else if (alarmHour < calendar.get(Calendar.HOUR_OF_DAY)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis() + interval, alarmPendingIntent);
            } // 세팅한 알람 시간이 현재 시간보다 작을 경우
            else if (alarmHour == calendar.get(Calendar.HOUR_OF_DAY)) {
                if (alarmMinute > calendar.get(Calendar.MINUTE)) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), alarmPendingIntent);
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis() + interval, alarmPendingIntent);
                }
            }
        }
        else {
            if(Build.VERSION.SDK_INT >= 19) {
                if (alarmHour > calendar.get(Calendar.HOUR_OF_DAY)) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), alarmPendingIntent);
                } // 세팅한 알람 시간이 현재 시간보다 클 경우
                else if (alarmHour < calendar.get(Calendar.HOUR_OF_DAY)) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis() + interval, alarmPendingIntent);
                } // 세팅한 알람 시간이 현재 시간보다 작을 경우
                else if (alarmHour == calendar.get(Calendar.HOUR_OF_DAY)) {
                    if (alarmMinute > calendar.get(Calendar.MINUTE)) {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), alarmPendingIntent);
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis() + interval, alarmPendingIntent);
                    }
                }
            } else {
                if (alarmHour > calendar.get(Calendar.HOUR_OF_DAY)) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), alarmPendingIntent);
                } // 세팅한 알람 시간이 현재 시간보다 클 경우
                else if (alarmHour < calendar.get(Calendar.HOUR_OF_DAY)) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis() + interval, alarmPendingIntent);
                } // 세팅한 알람 시간이 현재 시간보다 작을 경우
                else if (alarmHour == calendar.get(Calendar.HOUR_OF_DAY)) {
                    if (alarmMinute > calendar.get(Calendar.MINUTE)) {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis(), alarmPendingIntent);
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, alarmCalendar.getTimeInMillis() + interval, alarmPendingIntent);
                    }
                }
            }
        }
    }
    // 알람 On 설정

    public void alarm_Off() {
        alarmManager.cancel(alarmPendingIntent);
        alarmManager = null;
    }
    // 설정된 알람 취소

    public void showAlarmNotify() {
        PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this,0, new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder nbuilder = new NotificationCompat.Builder(this,"default");

        nbuilder.setSmallIcon(R.drawable.alarm);
        nbuilder.setContentTitle("알람");
        nbuilder.setOngoing(true);                  // 알람을 고정 시켜줌
        nbuilder.setContentIntent(pendingIntent);

        if(alarmHour > 0 && alarmHour < 12)
            nbuilder.setContentText("설정된 알람 시간은 오전 " + alarmHour + "시 " + alarmMinute + "분입니다.");
        else if(alarmHour == 12)
            nbuilder.setContentText("설정된 알람 시간은 오후 " + alarmHour + "시 " + alarmMinute + "분입니다.");
        else if(alarmHour > 12 && alarmHour <24)
            nbuilder.setContentText("설정된 알람 시간은 오후 " + (alarmHour-12) + "시 " + alarmMinute + "분입니다.");
        else if(alarmHour == 0)
            nbuilder.setContentText("설정된 알람 시간은 오전 " + "0시 " + alarmMinute + "분입니다.");

        alarm_notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alarm_notificationManager.createNotificationChannel(
                    new NotificationChannel("default", "기본채널",NotificationManager.IMPORTANCE_DEFAULT));
        }
        alarm_notificationManager.notify(1,nbuilder.build());

        Log.d(TAG,"show Alarm notify");
    }
    // Alarm 설정 시 Notification 활성화
}