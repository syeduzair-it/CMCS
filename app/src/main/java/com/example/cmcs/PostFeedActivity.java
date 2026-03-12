package com.example.cmcs;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cmcs.adapters.PostFeedAdapter;
import com.example.cmcs.models.PostModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PostFeedActivity extends AppCompatActivity {

    private RecyclerView rvPostFeed;
    private PostFeedAdapter adapter;
    private List<PostModel> postList = new ArrayList<>();
    private String scrollToPostId;
    private boolean isTeacher = false;
    private LinearLayoutManager layoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_feed);

        scrollToPostId = getIntent().getStringExtra("scrollToPostId");

        MaterialToolbar toolbar = findViewById(R.id.toolbarPostFeed);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvPostFeed = findViewById(R.id.rvPostFeed);
        layoutManager = new LinearLayoutManager(this);
        rvPostFeed.setLayoutManager(layoutManager);

        checkUserRoleAndLoad();
    }

    private void checkUserRoleAndLoad() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseDatabase.getInstance().getReference("users").child(uid).child("role")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String role = snapshot.getValue(String.class);
                        isTeacher = "teacher".equals(role);
                        loadPosts();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loadPosts();
                    }
                });
    }

    private void loadPosts() {
        FirebaseDatabase.getInstance().getReference("posts")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        postList.clear();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            PostModel post = ds.getValue(PostModel.class);
                            if (post != null) {
                                post.setPostId(ds.getKey());
                                postList.add(post);
                            }
                        }

                        Collections.sort(postList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                        adapter = new PostFeedAdapter(PostFeedActivity.this, postList, isTeacher);
                        rvPostFeed.setAdapter(adapter);

                        if (scrollToPostId != null) {
                            for (int i = 0; i < postList.size(); i++) {
                                if (scrollToPostId.equals(postList.get(i).getPostId())) {
                                    final int pos = i;
                                    rvPostFeed.post(() -> layoutManager.scrollToPositionWithOffset(pos, 0));
                                    break;
                                }
                            }
                        }

                        setupAutoPlayLogic();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private void setupAutoPlayLogic() {
        rvPostFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    playVideoInMaxVisibility();
                }
            }
        });

        rvPostFeed.postDelayed(this::playVideoInMaxVisibility, 500);
    }

    private void playVideoInMaxVisibility() {
        if (adapter == null || layoutManager == null) {
            return;
        }

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();

        if (firstVisible >= 0 && lastVisible >= 0) {
            // Find fully visible item
            int fullyVisibleItem = layoutManager.findFirstCompletelyVisibleItemPosition();
            if (fullyVisibleItem >= 0) {
                adapter.playVideoAtPosition(fullyVisibleItem, rvPostFeed);
            } else {
                adapter.playVideoAtPosition(firstVisible, rvPostFeed);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter != null) {
            adapter.pauseAll();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.releaseAllPlayers(rvPostFeed);
        }
    }
}
