package my.final_project;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

public class WeatherActivity extends AppCompatActivity {
    TextView textView,textView2;
    ImageView emotion,imageView1,imageView2,imageView3;
    LinearLayout mLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather);
        textView=(TextView)findViewById(R.id.textView);
        textView2=(TextView)findViewById(R.id.textView2);
        emotion=(ImageView)findViewById(R.id.emotion);
        mLayout=(LinearLayout)findViewById(R.id.mLayout);
        imageView1=(ImageView) findViewById(R.id.imageView1);
        imageView2=(ImageView) findViewById(R.id.imageView2);
        imageView3=(ImageView) findViewById(R.id.imageView3);

        Intent intent = getIntent();
        int dusts = intent.getIntExtra("dust",0);
        if(dusts<=30){
            textView.setText("매우좋음");
            textView2.setText("미세먼지가 없으니 추천할게 없지 뭐야");
            emotion.setImageResource(R.drawable.verygood);
            Glide.with(getApplicationContext())
                    .load(R.drawable.dungsil)
                    .fitCenter()
                    .into(imageView1);
            imageView2.setVisibility(View.GONE);
            Glide.with(getApplicationContext())
                    .load(R.drawable.bear)
                    .fitCenter()
                    .into(imageView3);
            mLayout.setBackgroundColor(Color.BLUE);
        }
        else if(dusts>30 && dusts<=80){
            textView.setText("보통");
            textView2.setText("보통이라고 방심 금물");
            emotion.setImageResource(R.drawable.good);
            mLayout.setBackgroundColor(Color.parseColor("#52E252"));
            Glide.with(getApplicationContext())
                    .load(R.drawable.botong1)
                    .fitCenter()
                    .into(imageView2);
            Glide.with(getApplicationContext())
                    .load(R.drawable.botong)
                    .fitCenter()
                    .into(imageView1);
            imageView3.setVisibility(View.GONE);
        }
        else if(dusts>80 && dusts<=150){
            textView.setText("나쁨");
            emotion.setImageResource(R.drawable.bad);
            mLayout.setBackgroundColor(Color.parseColor("#FFC81E"));
            textView2.setText("숯, 정화용 식물, 공기청정기 사용 추천");
            imageView1.setImageResource(R.drawable.deforest);
            imageView2.setImageResource(R.drawable.plant);
            imageView3.setImageResource(R.drawable.purifier);
        }
        else{
            textView.setText("매우 나쁨");
            textView2.setText("숯, 정화용 식물, 공기청정기 사용 추천");
            emotion.setImageResource(R.drawable.verybad);
            mLayout.setBackgroundColor(Color.parseColor("#EB0000"));
            imageView1.setImageResource(R.drawable.deforest);
            imageView2.setImageResource(R.drawable.plant);
            imageView3.setImageResource(R.drawable.purifier);
        }


    }
}
