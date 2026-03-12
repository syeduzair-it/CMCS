package com.example.cmcs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.cmcs.fragments.ChatsFragment;
import com.example.cmcs.fragments.HomeFragment;
import com.example.cmcs.fragments.MeFragment;
import com.example.cmcs.fragments.NoticeFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavView;
    FloatingActionButton fab;
    private String userRole = "student"; // Default securely to student

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavView = findViewById(R.id.bottomNavView);
        fab = findViewById(R.id.fabScanner);

        // Default fragment
        loadFragment(new HomeFragment());

        // Mark Home as selected by default
        bottomNavView.setSelectedItemId(R.id.nav_home);

        bottomNavView.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            Fragment selectedFragment = null;

            if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_chats) {
                selectedFragment = new ChatsFragment();
            } else if (id == R.id.nav_notice) {
                selectedFragment = new NoticeFragment();
            } else if (id == R.id.nav_me) {
                selectedFragment = new MeFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }

            return false;
        });

        fetchUserRole();

        fab.setOnClickListener(v -> {
            Intent intent;
            if ("teacher".equalsIgnoreCase(userRole)) {
                intent = new Intent(MainActivity.this, ClassSelectionActivity.class);
            } else {
                intent = new Intent(MainActivity.this, ScannerActivity.class);
            }
            startActivity(intent);
        });
    }

    private void fetchUserRole() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists() && snapshot.hasChild("role")) {
                        userRole = snapshot.child("role").getValue(String.class);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * Convenience stub — ChatsFragment hosts its own DrawerLayout. Extend here
     * if MainActivity ever owns the drawer directly.
     */
    public void setDrawerLockMode(int lockMode, View drawerView) {
        // No-op: drawer lives inside ChatsFragment, not MainActivity.
    }

    /**
     * Called by fragments (e.g. MeFragment) when the user taps the hamburger
     * icon on the CMCS standard toolbar. The drawer currently lives inside
     * ChatsFragment; this hook is a placeholder for when MainActivity owns a
     * global NavigationDrawer.
     */
    public void openDrawer() {
        // No-op for Phase 1 — MeFragment has no dedicated drawer.
        // Will be wired to a global NavigationDrawer in a future phase.
    }
}
