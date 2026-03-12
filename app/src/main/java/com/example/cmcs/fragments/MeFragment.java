package com.example.cmcs.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.cmcs.R;
import com.example.cmcs.adapters.MeMenuAdapter;
import com.example.cmcs.models.MeMenuItem;
import com.example.cmcs.models.UserModel;
import com.example.cmcs.welcome.WelcomeActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.example.cmcs.utils.CloudinaryUploader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * MeFragment — Phase 1.1
 *
 * Mirrors ChatsFragment's DrawerLayout pattern exactly: - DrawerLayout is the
 * root of fragment_me.xml - MaterialToolbar hamburger opens/closes the
 * NavigationView - No cross-activity delegation needed
 *
 * Also handles: role-based profile UI (student/teacher), compressed profile
 * image upload, and logout.
 */
public class MeFragment extends Fragment {

    // ── Constants ─────────────────────────────────────────────────────────
    private static final String PREFS_NAME = "cmcs_prefs";
    private static final int MAX_WIDTH = 1080;
    private static final int JPEG_QUALITY = 80;

    // ── Views — Drawer ────────────────────────────────────────────────────
    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private NavigationView navigationView;

    // ── Views — Profile card ──────────────────────────────────────────────
    private CircleImageView ivProfileImage;
    private ImageButton btnEditProfileImage;
    private ProgressBar pbUpload;
    private TextView tvName;
    private TextView tvBadge;
    private TextView tvEmail;
    private TextView tvDepartmentOrYear;
    private RecyclerView rvMenu;

    // ── State ─────────────────────────────────────────────────────────────
    /**
     * Tracks whether the DividerItemDecoration has been added to rvMenu.
     * Prevents duplicate dividers if populateProfile() is ever called again.
     */
    private boolean menuDecoratorAdded = false;

    // ── Firebase ──────────────────────────────────────────────────────────
    private String authUid;
    private DatabaseReference usersRef;

    // ── Gallery picker ────────────────────────────────────────────────────
    private ActivityResultLauncher<String> galleryLauncher;

    // ─────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleImageSelected(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_me, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
            @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Auth check
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            navigateToWelcome();
            return;
        }
        authUid = firebaseUser.getUid();

        // 2. Bind all views
        bindViews(view);

        // 3. Init Firebase references
        initFirebase();

        // 4. Wire toolbar hamburger → DrawerLayout
        //    (same pattern as ChatsFragment.setupToolbarAndDrawer)
        setupToolbarAndDrawer();

        // 5. Wire NavigationView item clicks
        setupNavigationDrawer();

        // 6. Wire edit-image button
        btnEditProfileImage.setOnClickListener(v
                -> galleryLauncher.launch("image/*"));

        // 7. Load user profile from Firebase
        loadUserProfile();
    }

    // Fix #2 — Clear Glide request on view destruction to prevent memory leaks
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ivProfileImage != null) {
            Glide.with(this).clear(ivProfileImage);
        }
        // Null out view references so GC can reclaim the view hierarchy
        ivProfileImage = null;
        btnEditProfileImage = null;
        pbUpload = null;
        tvName = null;
        tvBadge = null;
        tvEmail = null;
        tvDepartmentOrYear = null;
        rvMenu = null;
        drawerLayout = null;
        toolbar = null;
        navigationView = null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────
    private void bindViews(View view) {
        drawerLayout = view.findViewById(R.id.me_drawer_layout);
        toolbar = view.findViewById(R.id.me_toolbar);
        navigationView = view.findViewById(R.id.me_navigation_view);

        ivProfileImage = view.findViewById(R.id.ivProfileImage);
        btnEditProfileImage = view.findViewById(R.id.btnEditProfileImage);
        pbUpload = view.findViewById(R.id.pbUpload);
        tvName = view.findViewById(R.id.tvName);
        tvBadge = view.findViewById(R.id.tvBadge);
        tvEmail = view.findViewById(R.id.tvEmail);
        tvDepartmentOrYear = view.findViewById(R.id.tvDepartmentOrYear);
        rvMenu = view.findViewById(R.id.rvMenu);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Toolbar + Drawer  (mirrors ChatsFragment.setupToolbarAndDrawer exactly)
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Wire the hamburger icon to open/close the drawer. Identical pattern to
     * ChatsFragment.setupToolbarAndDrawer().
     */
    private void setupToolbarAndDrawer() {
        toolbar.setNavigationOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView);
            } else {
                drawerLayout.openDrawer(navigationView);
            }
        });
    }

    /**
     * Handle NavigationView item clicks. Mirrors
     * ChatsFragment.setupNavigationDrawer().
     */
    private void setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(navigationView);

            int id = item.getItemId();

            if (id == R.id.drawer_profile) {
                Toast.makeText(requireContext(),
                        "Profile — coming soon!", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.drawer_settings) {
                Toast.makeText(requireContext(),
                        "Settings — coming soon!", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.drawer_logout) {
                handleLogout();
            } else if (id == R.id.drawer_about) {
                Toast.makeText(requireContext(),
                        "CMCS — College of Management & Computer Science",
                        Toast.LENGTH_LONG).show();
            }

            return true;
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Firebase
    // ─────────────────────────────────────────────────────────────────────
    private void initFirebase() {
        usersRef = FirebaseDatabase.getInstance().getReference("users");
    }

    private void loadUserProfile() {
        usersRef.child(authUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!isAdded()) {
                            return;
                        }
                        UserModel user = snapshot.getValue(UserModel.class);
                        if (user == null) {
                            return;
                        }
                        populateProfile(user);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(),
                                    "Failed to load profile",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Profile population
    // ─────────────────────────────────────────────────────────────────────
    private void populateProfile(UserModel user) {
        // Name
        String name = user.getName();
        tvName.setText(name != null ? name : "Unknown User");

        // Email (from FirebaseAuth — authoritative source)
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null && firebaseUser.getEmail() != null) {
            tvEmail.setText(firebaseUser.getEmail());
        } else {
            tvEmail.setVisibility(View.GONE);
        }

        // Fix #4 — Null role fallback: default to student if role is null/unexpected
        String role = user.getRole();
        boolean isTeacher = "teacher".equalsIgnoreCase(role);
        // (any null or unrecognised role is treated as student — no crash)
        if (isTeacher) {
            tvBadge.setVisibility(View.VISIBLE);
            String dept = user.getDepartment();            // Fix #7 — null-safe
            tvDepartmentOrYear.setText(
                    dept != null && !dept.isEmpty() ? "Dept: " + dept : "");
            setupMenu(buildTeacherMenu());
        } else {
            tvBadge.setVisibility(View.GONE);
            String year = user.getYear();                  // Fix #7 — null-safe
            tvDepartmentOrYear.setText(
                    year != null && !year.isEmpty() ? "Year: " + year : "");
            setupMenu(buildStudentMenu());
        }

        // Profile image
        loadProfileImage(user.getProfileImage());

        // Populate nav header in drawer with same user data
        populateNavHeader(user);
    }

    /**
     * Populate the DrawerLayout nav header with the user's name/role/avatar.
     */
    private void populateNavHeader(UserModel user) {
        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) {
            return;
        }

        TextView navName = headerView.findViewById(R.id.nav_header_name);
        TextView navRole = headerView.findViewById(R.id.nav_header_role);
        de.hdodenhof.circleimageview.CircleImageView navAvatar
                = headerView.findViewById(R.id.nav_header_avatar);

        if (navName != null) {
            navName.setText(user.getName() != null ? user.getName() : "");
        }
        if (navRole != null) {
            boolean isTeacher = "teacher".equalsIgnoreCase(user.getRole());
            navRole.setText(isTeacher ? "Teacher" : "Student");
        }
        if (navAvatar != null) {
            String imgUrl = user.getProfileImage();
            if (imgUrl != null && !imgUrl.isEmpty()) {
                Glide.with(this)
                        .load(imgUrl)
                        .placeholder(R.drawable.ic_drawer_profile)
                        .circleCrop()
                        .into(navAvatar);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Profile image loading
    // ─────────────────────────────────────────────────────────────────────
    private void loadProfileImage(@Nullable String url) {
        if (!isAdded()) {
            return;
        }
        if (url != null && !url.isEmpty()) {
            Glide.with(requireContext())
                    .load(url)
                    .placeholder(R.drawable.ic_user)
                    .error(R.drawable.ic_user)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(ivProfileImage);
        } else {
            ivProfileImage.setImageResource(R.drawable.ic_user);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // RecyclerView menu
    // ─────────────────────────────────────────────────────────────────────
    private List<MeMenuItem> buildStudentMenu() {
        List<MeMenuItem> items = new ArrayList<>();
        items.add(new MeMenuItem("My Attendance", R.drawable.ic_notice));
        items.add(new MeMenuItem("Fees", R.drawable.ic_home));
        items.add(new MeMenuItem("Notes", R.drawable.ic_notice));
        items.add(new MeMenuItem("Marksheet", R.drawable.ic_notice));
        items.add(new MeMenuItem("University ID & Password", R.drawable.ic_drawer_profile));
        items.add(new MeMenuItem("Logout", R.drawable.ic_drawer_logout));
        return items;
    }

    private List<MeMenuItem> buildTeacherMenu() {
        List<MeMenuItem> items = new ArrayList<>();
        items.add(new MeMenuItem("My Time Table", R.drawable.ic_tp));
        items.add(new MeMenuItem("Uploaded Notes", R.drawable.ic_notice));
        items.add(new MeMenuItem("Notices Posted", R.drawable.ic_me));
        items.add(new MeMenuItem("Logout", R.drawable.ic_drawer_logout));
        return items;
    }

    private void setupMenu(List<MeMenuItem> items) {
        MeMenuAdapter adapter = new MeMenuAdapter(items, this::handleMenuItemClick);
        rvMenu.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMenu.setAdapter(adapter);
        rvMenu.setNestedScrollingEnabled(false);
        // Fix #1 — Only add the divider decoration once; skip on subsequent calls
        if (!menuDecoratorAdded) {
            rvMenu.addItemDecoration(
                    new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
            menuDecoratorAdded = true;
        }
    }

    private void handleMenuItemClick(MeMenuItem item) {
        switch (item.getLabel()) {
            case "Logout":
                handleLogout();
                break;
            case "My Attendance":
                startActivity(new Intent(requireContext(), com.example.cmcs.MyAttendanceActivity.class));
                break;
            case "Fees":
                startActivity(new Intent(requireContext(), com.example.cmcs.FeesActivity.class));
                break;
            case "Marksheet":
                startActivity(new Intent(requireContext(), com.example.cmcs.MarksheetActivity.class));
                break;
            case "University ID & Password":
                startActivity(new Intent(requireContext(), com.example.cmcs.UniversityCredentialsActivity.class));
                break;
            case "My Time Table":
                startActivity(new Intent(requireContext(), com.example.cmcs.TeacherTimeTableActivity.class));
                break;
            case "Notes":
                startActivity(new Intent(requireContext(), com.example.cmcs.NotesClassActivity.class));
                break;
            case "Uploaded Notes":
                startActivity(new Intent(requireContext(), com.example.cmcs.NotesClassActivity.class));
                break;
            case "Notices Posted":
                startActivity(new Intent(requireContext(), com.example.cmcs.NoticesPostedActivity.class));
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Image pick → compress → upload
    // ─────────────────────────────────────────────────────────────────────
    private void handleImageSelected(Uri uri) {
        File compressed = compressImage(uri);
        if (compressed == null) {
            Toast.makeText(requireContext(),
                    "Could not process image", Toast.LENGTH_SHORT).show();
            return;
        }

        setUploadLoading(true);

        CloudinaryUploader.upload(
                requireContext(),
                Uri.fromFile(compressed),
                "profile_images",
                new CloudinaryUploader.Callback() {
            @Override
            public void onProgress(int percent) {
                // pbUpload is indeterminate; no-op
            }

            @Override
            public void onSuccess(String secureUrl) {
                usersRef.child(authUid)
                        .child("profileImage")
                        .setValue(secureUrl)
                        .addOnCompleteListener(task -> {
                            if (!isAdded()) {
                                return;
                            }
                            setUploadLoading(false);
                            if (task.isSuccessful()) {
                                loadProfileImage(secureUrl);
                                Toast.makeText(requireContext(),
                                        "Profile updated",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(requireContext(),
                                        "Failed to save image URL",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }

            @Override
            public void onFailure(String error) {
                if (!isAdded()) {
                    return;
                }
                setUploadLoading(false);
                Toast.makeText(requireContext(),
                        "Upload failed. Please try again.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Compress image selected from gallery. Max width: 1080 px, JPEG quality:
     * 80%. Saved to cacheDir/compressed_profile.jpg.
     */
    @Nullable
    private File compressImage(Uri uri) {
        try {
            // Decode bounds (memory-safe)
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream bs = requireContext().getContentResolver().openInputStream(uri)) {
                if (bs == null) {
                    return null;
                }
                BitmapFactory.decodeStream(bs, null, bounds);
            }

            // Calculate inSampleSize
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = bounds.outWidth > MAX_WIDTH
                    ? Math.max(1, bounds.outWidth / MAX_WIDTH) : 1;

            Bitmap bitmap;
            try (InputStream ds = requireContext().getContentResolver().openInputStream(uri)) {
                if (ds == null) {
                    return null;
                }
                bitmap = BitmapFactory.decodeStream(ds, null, opts);
            }
            if (bitmap == null) {
                return null;
            }

            // Scale further if still too wide
            if (bitmap.getWidth() > MAX_WIDTH) {
                float scale = (float) MAX_WIDTH / bitmap.getWidth();
                int newH = Math.round(bitmap.getHeight() * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, MAX_WIDTH, newH, true);
            }

            File outFile = new File(requireContext().getCacheDir(), "compressed_profile.jpg");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
            }
            bitmap.recycle();
            return outFile;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setUploadLoading(boolean loading) {
        if (!isAdded()) {
            return;
        }
        btnEditProfileImage.setEnabled(!loading);
        pbUpload.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────
    private void handleLogout() {
        // 1. Clear SharedPreferences
        requireActivity()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();

        // 2. Sign out from Firebase
        FirebaseAuth.getInstance().signOut();

        // 3. Navigate to WelcomeActivity, clear the entire task stack
        Intent intent = new Intent(requireContext(), WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // Fix #6 — Smooth fade-out transition
        requireActivity().overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out);

        // 4. Finish all activities so back-press cannot return to the app
        requireActivity().finishAffinity();
    }

    private void navigateToWelcome() {
        Intent intent = new Intent(requireContext(), WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (!requireActivity().isFinishing()) {
            requireActivity().finishAffinity();
        }
    }
}
