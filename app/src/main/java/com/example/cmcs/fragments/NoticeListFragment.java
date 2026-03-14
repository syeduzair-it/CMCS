package com.example.cmcs.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.R;
import com.example.cmcs.adapters.NoticeAdapter;
import com.example.cmcs.models.NoticeModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reusable fragment that displays a list of notices from a given Firebase DB
 * path.
 *
 * Arguments: ARG_DB_PATH — Firebase Realtime DB path (e.g. "notices/college")
 * ARG_ROLE — current user's role ("student" | "teacher") ARG_CURRENT_UID —
 * current user's UID
 */
public class NoticeListFragment extends Fragment {

    public static final String ARG_DB_PATH = "db_path";
    public static final String ARG_ROLE = "role";
    public static final String ARG_CURRENT_UID = "current_uid";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;

    private NoticeAdapter adapter;
    private List<NoticeModel> noticeList = new ArrayList<>();

    private String dbPath;
    private String role;
    private String currentUid;

    private ValueEventListener valueEventListener;
    private Query firebaseQuery;

    // ── Factory ──────────────────────────────────────────────────────────
    public static NoticeListFragment newInstance(String dbPath, String role, String currentUid) {
        NoticeListFragment f = new NoticeListFragment();
        Bundle b = new Bundle();
        b.putString(ARG_DB_PATH, dbPath);
        b.putString(ARG_ROLE, role);
        b.putString(ARG_CURRENT_UID, currentUid);
        f.setArguments(b);
        return f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            dbPath = getArguments().getString(ARG_DB_PATH);
            role = getArguments().getString(ARG_ROLE);
            currentUid = getArguments().getString(ARG_CURRENT_UID);
        }
        if (currentUid == null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notice_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.noticeRecyclerView);
        progressBar = view.findViewById(R.id.noticeProgressBar);
        emptyState = view.findViewById(R.id.emptyStateLayout);

        adapter = new NoticeAdapter(requireContext(), noticeList, currentUid, role, dbPath);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        loadNotices();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Detach listener to prevent memory leaks
        if (firebaseQuery != null && valueEventListener != null) {
            firebaseQuery.removeEventListener(valueEventListener);
        }
    }

    // ── Firebase ──────────────────────────────────────────────────────────
    private void loadNotices() {
        if (dbPath == null || dbPath.isEmpty()) {
            android.util.Log.e("NoticeListFragment", "loadNotices: dbPath is null or empty");
            return;
        }

        android.util.Log.d("NoticeListFragment", "Loading notices from path: " + dbPath);

        // Order by timestamp server-side; we'll reverse in memory for DESC display
        firebaseQuery = FirebaseDatabase.getInstance()
                .getReference(dbPath)
                .orderByChild("timestamp");

        valueEventListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) {
                    return;
                }

                List<NoticeModel> fresh = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    NoticeModel n = child.getValue(NoticeModel.class);
                    if (n != null) {
                        // ── CRITICAL FIX ───────────────────────────────────────
                        // ALWAYS set noticeId from the Firebase key, even if it's already set
                        // This ensures we never use incorrect values like course names
                        String firebaseKey = child.getKey();
                        if (firebaseKey != null && !firebaseKey.isEmpty()) {
                            n.setNoticeId(firebaseKey);
                            android.util.Log.d("NoticeListFragment", 
                                "Loaded notice with ID: " + firebaseKey + 
                                ", Title: " + n.getTitle());
                        } else {
                            android.util.Log.e("NoticeListFragment", 
                                "Notice has null Firebase key, skipping");
                            continue; // Skip this notice if key is invalid
                        }
                        
                        // Validate that the noticeId is a proper Firebase push key
                        if (!firebaseKey.startsWith("-") || firebaseKey.length() < 15) {
                            android.util.Log.w("NoticeListFragment", 
                                "Notice has unusual key format: " + firebaseKey);
                        }
                        
                        fresh.add(n);
                    }
                }

                // Sort DESC (latest first)
                Collections.sort(fresh, (a, b)
                        -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                progressBar.setVisibility(View.GONE);

                noticeList.clear();
                noticeList.addAll(fresh);
                adapter.notifyDataSetChanged();

                android.util.Log.d("NoticeListFragment", 
                    "Loaded " + noticeList.size() + " notices from " + dbPath);

                emptyState.setVisibility(noticeList.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(noticeList.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    progressBar.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                    android.util.Log.e("NoticeListFragment", 
                        "Failed to load notices from " + dbPath + 
                        ", Error: " + error.getMessage());
                }
            }
        };

        firebaseQuery.addValueEventListener(valueEventListener);
    }
}
