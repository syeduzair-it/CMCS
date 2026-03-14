package com.example.cmcs;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.example.cmcs.fragments.ChatsFragment;
import com.example.cmcs.fragments.HomeFragment;
import com.example.cmcs.fragments.MeFragment;
import com.example.cmcs.fragments.NoticeFragment;
import com.google.android.material.appbar.MaterialToolbar;
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

    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavView;
    private FloatingActionButton fabScanner;  // Permanent scanner FAB (centered)
    private FloatingActionButton fabAction;   // Dynamic action FAB (bottom-right)
    private String userRole = "student"; // Default securely to student
    private Fragment currentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        bottomNavView = findViewById(R.id.bottomNavView);
        fabScanner = findViewById(R.id.fabScanner);
        fabAction = findViewById(R.id.fabAction);

        // Set toolbar as action bar
        setSupportActionBar(toolbar);

        // Wire toolbar navigation icon to open drawer (when applicable)
        toolbar.setNavigationOnClickListener(v -> {
            if (currentFragment instanceof ChatsFragment) {
                ((ChatsFragment) currentFragment).openDrawer();
            } else if (currentFragment instanceof MeFragment) {
                ((MeFragment) currentFragment).openDrawer();
            }
        });

        // Default fragment
        loadFragment(new HomeFragment(), "Home");

        // Mark Home as selected by default
        bottomNavView.setSelectedItemId(R.id.nav_home);

        bottomNavView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment selectedFragment = null;
            String title = "CMCS";

            if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
                title = "Home";
            } else if (id == R.id.nav_chats) {
                selectedFragment = new ChatsFragment();
                title = "Chats";
            } else if (id == R.id.nav_notice) {
                selectedFragment = new NoticeFragment();
                title = "Notices";
            } else if (id == R.id.nav_me) {
                selectedFragment = new MeFragment();
                title = "Profile";
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment, title);
                return true;
            }

            return false;
        });

        fetchUserRole();

        // Scanner FAB click behavior — role-based routing
        fabScanner.setOnClickListener(v -> {
            Intent intent;
            if ("teacher".equalsIgnoreCase(userRole)) {
                // Teachers: Open class selection for attendance management
                intent = new Intent(MainActivity.this, ClassSelectionActivity.class);
            } else {
                // Students: Open QR scanner to mark attendance
                intent = new Intent(MainActivity.this, ScannerActivity.class);
            }
            startActivity(intent);
        });

        // Action FAB initially hidden — configured per fragment in loadFragment()
        fabAction.hide();
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

    private void loadFragment(Fragment fragment, String title) {
        currentFragment = fragment;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        
        // Update toolbar title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
        
        // Show/hide navigation icon based on fragment
        if (fragment instanceof ChatsFragment || fragment instanceof MeFragment) {
            toolbar.setNavigationIcon(R.drawable.ic_menu);
        } else {
            toolbar.setNavigationIcon(null);
        }
        
        // Configure Action FAB visibility and behavior per fragment
        // Scanner FAB remains permanently visible
        configureActionFabForFragment(fragment);
    }
    
    /**
     * Configure Action FAB visibility and click behavior based on active fragment.
     * Scanner FAB remains permanently visible and is never modified.
     * 
     * Rules:
     * - HomeFragment: Hide Action FAB
     * - ChatsFragment: Show Action FAB (opens SelectUserActivity)
     * - NoticeFragment: Hide Action FAB (NoticeFragment has its own FAB)
     * - MeFragment: Hide Action FAB
     */
    private void configureActionFabForFragment(Fragment fragment) {
        if (fragment instanceof ChatsFragment) {
            // Show Action FAB for new chat
            fabAction.show();
            fabAction.setImageResource(R.drawable.ic_new_chat);
            fabAction.setContentDescription("New Chat");
            fabAction.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, SelectUserActivity.class);
                startActivity(intent);
            });
        } else if (fragment instanceof NoticeFragment) {
            // Hide Action FAB — NoticeFragment has its own
            fabAction.hide();
        } else {
            // Hide Action FAB for Home and Me fragments
            fabAction.hide();
        }
    }
    
    /**
     * Public method to allow fragments to control Action FAB visibility.
     * Used by NoticeFragment to hide MainActivity Action FAB when it shows its own.
     */
    public void hideActionFab() {
        if (fabAction != null) {
            fabAction.hide();
        }
    }
    
    /**
     * Public method to show Action FAB with custom configuration.
     * Used by fragments that need dynamic Action FAB control.
     */
    public void showActionFab(int iconRes, String description, View.OnClickListener listener) {
        if (fabAction != null) {
            fabAction.setImageResource(iconRes);
            fabAction.setContentDescription(description);
            fabAction.setOnClickListener(listener);
            fabAction.show();
        }
    }
    
    /**
     * Public method to get user role.
     * Used by fragments that need role-based UI logic.
     */
    public String getUserRole() {
        return userRole;
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
        // Delegate to current fragment if it supports drawer
        if (currentFragment instanceof ChatsFragment) {
            ((ChatsFragment) currentFragment).openDrawer();
        } else if (currentFragment instanceof MeFragment) {
            ((MeFragment) currentFragment).openDrawer();
        }
    }
}
