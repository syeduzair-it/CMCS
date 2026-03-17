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
    private MeMenuAdapter menuAdapter;

    // ── Notes badge (student only) ────────────────────────────────────────
    private DatabaseReference notesBadgeRef;
    private ValueEventListener notesBadgeListener;
    private String cachedDept, cachedCourse, cachedYear;

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

        // 4. Wire edit-image button
        btnEditProfileImage.setOnClickListener(v
                -> galleryLauncher.launch("image/*"));

        // 5. Load user profile from Firebase
        loadUserProfile();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Detach notes badge listener
        if (notesBadgeRef != null && notesBadgeListener != null) {
            notesBadgeRef.removeEventListener(notesBadgeListener);
        }
        if (ivProfileImage != null) {
            Glide.with(this).clear(ivProfileImage);
        }
        ivProfileImage = null;
        btnEditProfileImage = null;
        pbUpload = null;
        tvName = null;
        tvBadge = null;
        tvEmail = null;
        tvDepartmentOrYear = null;
        rvMenu = null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────
    private void bindViews(View view) {
        ivProfileImage      = view.findViewById(R.id.ivProfileImage);
        btnEditProfileImage = view.findViewById(R.id.btnEditProfileImage);
        pbUpload            = view.findViewById(R.id.pbUpload);
        tvName              = view.findViewById(R.id.tvName);
        tvBadge             = view.findViewById(R.id.tvBadge);
        tvEmail             = view.findViewById(R.id.tvEmail);
        tvDepartmentOrYear  = view.findViewById(R.id.tvDepartmentOrYear);
        rvMenu              = view.findViewById(R.id.rvMenu);
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
            // Cache for badge listener
            cachedDept   = user.getDepartment();
            cachedCourse = user.getCourse();
            cachedYear   = user.getYear();
            startNotesBadgeListener();
        }

        // Profile image
        loadProfileImage(user.getProfileImage());
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
        items.add(new MeMenuItem("My Attendance", R.drawable.ic_attendence));
        items.add(new MeMenuItem("Fees", R.drawable.ic_fees));
        items.add(new MeMenuItem("Notes", R.drawable.ic_notes));
        items.add(new MeMenuItem("Marksheet", R.drawable.ic_marksheet));
        items.add(new MeMenuItem("Timetable", R.drawable.ic_timetable));
        items.add(new MeMenuItem("ID Card", R.drawable.ic_id_card));
        items.add(new MeMenuItem("University ID & Password", R.drawable.ic_drawer_profile));
        items.add(new MeMenuItem("CMCS Website", R.drawable.ic_web));
        items.add(new MeMenuItem("Logout", R.drawable.ic_drawer_logout));
        return items;
    }

    private List<MeMenuItem> buildTeacherMenu() {
        List<MeMenuItem> items = new ArrayList<>();
        items.add(new MeMenuItem("My Time Table", R.drawable.ic_timetable));
        items.add(new MeMenuItem("Upload Timetable", R.drawable.ic_timetable));
        items.add(new MeMenuItem("Upload Notes", R.drawable.ic_notes));
        items.add(new MeMenuItem("Notices Posted", R.drawable.ic_notice));
        items.add(new MeMenuItem("ID Card", R.drawable.ic_id_card));
        items.add(new MeMenuItem("CMCS Website", R.drawable.ic_web));
        items.add(new MeMenuItem("Logout", R.drawable.ic_drawer_logout));
        return items;
    }

    private void setupMenu(List<MeMenuItem> items) {
        menuAdapter = new MeMenuAdapter(items, this::handleMenuItemClick);
        rvMenu.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMenu.setAdapter(menuAdapter);
        rvMenu.setNestedScrollingEnabled(false);
        // Fix #1 — Only add the divider decoration once; skip on subsequent calls
        if (!menuDecoratorAdded) {
            rvMenu.addItemDecoration(
                    new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
            menuDecoratorAdded = true;
        }
    }

    // ── Notes badge (student only) ────────────────────────────────────────

    /**
     * Listens to notes/{dept}/{course}/{year} in real time.
     * Shows a dot badge on the "Notes" menu item when any note is unread.
     * Reads noteViews/{noteId}/{uid} per note (scoped) — no full-node scan.
     */
    private void startNotesBadgeListener() {
        if (cachedDept == null || cachedCourse == null || cachedYear == null) return;
        if (authUid == null) return;

        notesBadgeRef = FirebaseDatabase.getInstance()
                .getReference("notes")
                .child(sanitize(cachedDept))
                .child(cachedCourse != null ? cachedCourse.trim().toLowerCase() : "_")
                .child(sanitize(cachedYear));

        notesBadgeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> noteIds = new ArrayList<>();
                for (DataSnapshot subject : snapshot.getChildren()) {
                    for (DataSnapshot note : subject.getChildren()) {
                        String key = note.getKey();
                        if (key != null && !key.equals("_created")) noteIds.add(key);
                    }
                }
                if (noteIds.isEmpty()) {
                    setNotesBadge(false);
                    return;
                }
                // Check each note's view entry individually (scoped reads)
                java.util.concurrent.atomic.AtomicInteger pending =
                        new java.util.concurrent.atomic.AtomicInteger(noteIds.size());
                java.util.concurrent.atomic.AtomicBoolean hasUnread =
                        new java.util.concurrent.atomic.AtomicBoolean(false);
                for (String noteId : noteIds) {
                    FirebaseDatabase.getInstance()
                            .getReference("noteViews").child(noteId).child(authUid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot s) {
                                    if (!s.exists()) hasUnread.set(true);
                                    if (pending.decrementAndGet() == 0) setNotesBadge(hasUnread.get());
                                }
                                @Override public void onCancelled(@NonNull DatabaseError e) {
                                    if (pending.decrementAndGet() == 0) setNotesBadge(hasUnread.get());
                                }
                            });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {
                setNotesBadge(false);
            }
        };
        notesBadgeRef.addValueEventListener(notesBadgeListener);
    }

    private void setNotesBadge(boolean show) {
        if (!isAdded() || menuAdapter == null) return;
        menuAdapter.setBadge("Notes", show);
    }

    private static String sanitize(String s) {
        if (s == null) return "_";
        return s.trim().replace(" ", "_").replace(".", "_")
                .replace("#", "_").replace("$", "_")
                .replace("[", "_").replace("]", "_");
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
            case "Timetable":
                startActivity(new Intent(requireContext(), com.example.cmcs.StudentTimetableActivity.class));
                break;
            case "ID Card":
                startActivity(new Intent(requireContext(), com.example.cmcs.IdCardActivity.class));
                break;
            case "University ID & Password":
                startActivity(new Intent(requireContext(), com.example.cmcs.UniversityCredentialsActivity.class));
                break;
            case "My Time Table":
                startActivity(new Intent(requireContext(), com.example.cmcs.TeacherTimeTableActivity.class));
                break;
            case "Upload Timetable":
                Intent intent = new Intent(requireContext(), com.example.cmcs.ClassSelectionActivity.class);
                intent.putExtra(com.example.cmcs.ClassSelectionActivity.EXTRA_MODE, 
                        com.example.cmcs.ClassSelectionActivity.MODE_TIMETABLE);
                startActivity(intent);
                break;
            case "Notes":
                startActivity(new Intent(requireContext(), com.example.cmcs.NotesClassActivity.class));
                break;
            case "Upload Notes":
                startActivity(new Intent(requireContext(), com.example.cmcs.UploadNotesClassActivity.class));
                break;
            case "Notices Posted":
                startActivity(new Intent(requireContext(), com.example.cmcs.NoticesPostedActivity.class));
                break;
            case "CMCS Website":
                openCmcsWebsite();
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
    // CMCS Website
    // ─────────────────────────────────────────────────────────────────────
    private void openCmcsWebsite() {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.cmcs.hjes.in/new/"));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(getContext(), "No browser installed", Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────
    private void handleLogout() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void performLogout() {
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

        requireActivity().overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out);

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
