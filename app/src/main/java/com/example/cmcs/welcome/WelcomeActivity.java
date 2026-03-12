package com.example.cmcs.welcome;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.cmcs.R;
import com.example.cmcs.login.Login;

import java.util.Arrays;
import java.util.List;

import me.relex.circleindicator.CircleIndicator3;

public class WelcomeActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private WelcomeSliderAdapter sliderAdapter;
    Button studentLoginBtn, teacherLoginBtn;
    private Handler sliderHandler;
    private Runnable sliderRunnable;

    private List<SlideItem> slideItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_screen);

        viewPager = findViewById(R.id.viewPager);

        studentLoginBtn = findViewById(R.id.studentLoginBtn);
        teacherLoginBtn = findViewById(R.id.teacherLoginBtn);

        // ✅ Slide data
        slideItems = Arrays.asList(
                new SlideItem(R.drawable.slide1, "Welcome Student", "Access your class chats, notices and more."),
                new SlideItem(R.drawable.slide2, "Teachers Panel", "Post updates, chat with students and manage classes."),
                new SlideItem(R.drawable.slide3, "CMCS App", "Your digital college communication system.")
        );

        // ✅ Adapter setup
        sliderAdapter = new WelcomeSliderAdapter(slideItems);
        viewPager.setAdapter(sliderAdapter);

        // ✅ Dots setup (after adapter)
        CircleIndicator3 indicator = findViewById(R.id.circleIndicator);
        indicator.setViewPager(viewPager);

        // ✅ Auto-slide
        sliderHandler = new Handler();
        sliderRunnable = () -> {
            if (slideItems != null && !slideItems.isEmpty()) {
                int nextItem = (viewPager.getCurrentItem() + 1) % slideItems.size();
                viewPager.setCurrentItem(nextItem, true);
                sliderHandler.postDelayed(sliderRunnable, 3000);
            }
        };
        sliderHandler.postDelayed(sliderRunnable, 3000);

        // ✅ Reset timer when swiped
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                sliderHandler.removeCallbacks(sliderRunnable);
                sliderHandler.postDelayed(sliderRunnable, 3000);
            }
        });

        // ✅ Fade scale animation
        viewPager.setPageTransformer(new ViewPager2.PageTransformer() {
            @Override
            public void transformPage(@NonNull View page, float position) {
                if (position < -1 || position > 1) {
                    page.setAlpha(0f);
                    page.setVisibility(View.INVISIBLE);
                } else {
                    page.setVisibility(View.VISIBLE);
                    page.setAlpha(1 - Math.abs(position));
                    page.setTranslationX(-position * page.getWidth());
                    float scale = 0.85f + (1 - Math.abs(position)) * 0.15f;
                    page.setScaleX(scale);
                    page.setScaleY(scale);
                }
            }
        });

        studentLoginBtn.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, Login.class);
            intent.putExtra("loginRole", "student");
            startActivity(intent);
        });

        teacherLoginBtn.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, Login.class);
            intent.putExtra("loginRole", "teacher");
            startActivity(intent);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sliderHandler != null) {
            sliderHandler.removeCallbacks(sliderRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sliderHandler != null && sliderRunnable != null) {
            sliderHandler.postDelayed(sliderRunnable, 3000);
        }
    }
}
