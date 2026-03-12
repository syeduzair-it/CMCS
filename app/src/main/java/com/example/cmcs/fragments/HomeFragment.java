package com.example.cmcs.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.cmcs.AddHighlightActivity;
import com.example.cmcs.MainActivity;
import com.example.cmcs.R;
import com.example.cmcs.adapters.FeatureAdapter;
import com.example.cmcs.adapters.HighlightAdapter;
import com.example.cmcs.adapters.StoryAdapter;
import com.example.cmcs.adapters.PostFeedAdapter;
import com.example.cmcs.adapters.PostGridAdapter;
import com.example.cmcs.models.FeatureModel;
import com.example.cmcs.models.HighlightModel;
import com.example.cmcs.models.StoryModel;
import com.example.cmcs.models.PostModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import androidx.appcompat.app.AlertDialog;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import me.relex.circleindicator.CircleIndicator3;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HomeFragment — Phase-2
 *
 * Sections loaded independently: 1. Stories (college + teacher, 24h filter,
 * role-ordered) 2. Features (ViewPager2 with auto-scroll every 5s; teacher
 * add/delete) 3. Highlights (horizontal circles; teacher "Add" button)
 *
 * Pull-to-refresh reloads all three sections.
 */
public class HomeFragment extends Fragment {

    private static final long TWENTY_FOUR_HOURS = 24L * 60 * 60 * 1000;
    private static final long FEATURE_AUTO_SCROLL_MS = 5_000L;
    private static final int STORY_READS = 2;

    // ── Views ─────────────────────────────────────────────────────────────
    private RecyclerView rvStories, rvHighlights, rvFeatures, rvPosts;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutFeaturesSection, layoutHighlightsSection, layoutPostsSection;
    private MaterialButton btnAddHighlight, btnAddFeature;
    private MaterialButtonToggleGroup togglePostView;

    // ── State ─────────────────────────────────────────────────────────────
    private String currentUid;
    private boolean isTeacher;
    private com.google.firebase.database.DatabaseReference featuresRef;
    private com.google.firebase.database.ValueEventListener featuresListener;
    private com.google.firebase.database.ValueEventListener postsListener;
    private com.google.firebase.database.ValueEventListener collegeStoriesListener;
    private com.google.firebase.database.ValueEventListener teacherStoriesListener;

    private PostFeedAdapter postFeedAdapter;
    private PostGridAdapter postGridAdapter;
    private List<PostModel> postList = new ArrayList<>();
    private boolean isPostListView = true;
    private LinearLayoutManager postFeedLayoutManager;
    private androidx.recyclerview.widget.GridLayoutManager postGridLayoutManager;

    // ── Handlers ────────────────────────────────────────────────
    // ── Refresh counter (stories + features + highlights + posts) ─────────────────
    private static final int TOTAL_SECTIONS = 4;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialToolbar toolbar = view.findViewById(R.id.cmcsToolbar);
        toolbar.setNavigationOnClickListener(
                v -> ((MainActivity) requireActivity()).openDrawer());

        rvStories = view.findViewById(R.id.rvStories);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        rvFeatures = view.findViewById(R.id.rvFeatures);
        rvHighlights = view.findViewById(R.id.rvHighlights);
        layoutFeaturesSection = view.findViewById(R.id.layoutFeaturesSection);
        layoutHighlightsSection = view.findViewById(R.id.layoutHighlightsSection);
        layoutPostsSection = view.findViewById(R.id.layoutPostsSection);
        btnAddHighlight = view.findViewById(R.id.btnAddHighlight);
        btnAddFeature = view.findViewById(R.id.btnAddFeature);
        togglePostView = view.findViewById(R.id.togglePostView);
        rvPosts = view.findViewById(R.id.rvPosts);

        rvStories.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvHighlights.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvFeatures.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.VERTICAL, false));

        postFeedLayoutManager = new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false);
        postGridLayoutManager = new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 3);
        rvPosts.setLayoutManager(postFeedLayoutManager);

        togglePostView.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnListView) {
                    isPostListView = true;
                    rvPosts.setLayoutManager(postFeedLayoutManager);
                    if (postFeedAdapter != null) {
                        rvPosts.setAdapter(postFeedAdapter);
                    }
                } else if (checkedId == R.id.btnGridView) {
                    isPostListView = false;
                    if (postFeedAdapter != null) {
                        postFeedAdapter.pauseAll();
                    }
                    rvPosts.setLayoutManager(postGridLayoutManager);
                    if (postGridAdapter != null) {
                        rvPosts.setAdapter(postGridAdapter);
                    }
                }
            }
        });

        rvPosts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE && isPostListView && postFeedAdapter != null) {
                    int firstVisible = postFeedLayoutManager.findFirstVisibleItemPosition();
                    int lastVisible = postFeedLayoutManager.findLastVisibleItemPosition();
                    if (firstVisible >= 0 && lastVisible >= 0) {
                        int fullyVisibleItem = postFeedLayoutManager.findFirstCompletelyVisibleItemPosition();
                        if (fullyVisibleItem >= 0) {
                            postFeedAdapter.playVideoAtPosition(fullyVisibleItem, rvPosts);
                        } else {
                            postFeedAdapter.playVideoAtPosition(firstVisible, rvPosts);
                        }
                    }
                }
            }
        });

        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(this::loadAllSections);

        // Resolve role then load
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }
        currentUid = user.getUid();

        FirebaseDatabase.getInstance().getReference("users").child(currentUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot s) {
                        String role = s.child("role").getValue(String.class);
                        isTeacher = "teacher".equals(role);
                        configureTeacherControls();
                        loadAllSections();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        loadAllSections();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (featuresRef != null && featuresListener != null) {
            featuresRef.removeEventListener(featuresListener);
        }
        if (postsListener != null) {
            FirebaseDatabase.getInstance().getReference("posts").removeEventListener(postsListener);
        }
        if (collegeStoriesListener != null) {
            FirebaseDatabase.getInstance().getReference("stories/college").removeEventListener(collegeStoriesListener);
        }
        if (teacherStoriesListener != null) {
            FirebaseDatabase.getInstance().getReference("stories/teachers").removeEventListener(teacherStoriesListener);
        }
        if (postFeedAdapter != null) {
            postFeedAdapter.releaseAllPlayers(rvPosts);
        }
    }

    private void configureTeacherControls() {
        if (isTeacher) {
            btnAddHighlight.setVisibility(View.VISIBLE);
            btnAddHighlight.setOnClickListener(v
                    -> startActivity(new Intent(requireContext(), AddHighlightActivity.class)));

            if (btnAddFeature != null) {
                btnAddFeature.setOnClickListener(v -> {
                    startActivity(new Intent(requireContext(), com.example.cmcs.AddFeatureActivity.class));
                });
            }
        } else {
            btnAddHighlight.setVisibility(View.GONE);
            if (btnAddFeature != null) {
                btnAddFeature.setVisibility(View.GONE);
            }
        }
    }

    // ── Load all sections ─────────────────────────────────────────────────
    private void loadAllSections() {
        if (!isAdded()) {
            return;
        }
        AtomicInteger done = new AtomicInteger(0);
        Runnable onSectionDone = () -> {
            if (done.incrementAndGet() == TOTAL_SECTIONS) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(
                            () -> swipeRefresh.setRefreshing(false));
                }
            }
        };
        loadStories(onSectionDone);
        loadFeatures(onSectionDone);
        loadHighlights(onSectionDone);
        loadPosts(onSectionDone);
    }

    // ── Stories ───────────────────────────────────────────────────────────
    private void loadStories(Runnable onDone) {
        final List<StoryModel> college = new ArrayList<>();
        final List<StoryModel> teachers = new ArrayList<>();
        final AtomicInteger done = new AtomicInteger(0);
        final long now = System.currentTimeMillis();

        if (collegeStoriesListener != null) {
            FirebaseDatabase.getInstance().getReference("stories/college").removeEventListener(collegeStoriesListener);
        }

        collegeStoriesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                synchronized (college) {
                    college.clear();
                }
                for (DataSnapshot c : s.getChildren()) {
                    StoryModel m = c.getValue(StoryModel.class);
                    if (m == null) {
                        continue;
                    }
                    m.setStoryId(c.getKey());
                    m.setType("college");
                    if (now - m.getTimestamp() <= TWENTY_FOUR_HOURS)
                        synchronized (college) {
                        college.add(m);
                    }
                }
                if (done.incrementAndGet() >= STORY_READS) {
                    renderStories(college, teachers, onDone);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                if (done.incrementAndGet() >= STORY_READS) {
                    renderStories(college, teachers, onDone);
                }
            }
        };
        FirebaseDatabase.getInstance().getReference("stories/college").addValueEventListener(collegeStoriesListener);

        if (teacherStoriesListener != null) {
            FirebaseDatabase.getInstance().getReference("stories/teachers").removeEventListener(teacherStoriesListener);
        }

        teacherStoriesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                synchronized (teachers) {
                    teachers.clear();
                }
                for (DataSnapshot ts : s.getChildren()) {
                    // Determine if this snapshot is a direct story object or a grouping folder
                    if (ts.hasChild("timestamp") && ts.hasChild("mediaUrl") && ts.hasChild("teacherUid")) {
                        // Direct story object
                        StoryModel m = ts.getValue(StoryModel.class);
                        if (m != null) {
                            m.setStoryId(ts.getKey());
                            m.setType("teacher");
                            Log.d("StoryDebug", "Teacher story loaded: " + m.getStoryId());
                            if (now - m.getTimestamp() <= TWENTY_FOUR_HOURS) {
                                synchronized (teachers) {
                                    teachers.add(m);
                                }
                            }
                        }
                    } else {
                        // Nested under teacherUid
                        for (DataSnapshot sc : ts.getChildren()) {
                            StoryModel m = sc.getValue(StoryModel.class);
                            if (m != null) {
                                m.setStoryId(sc.getKey());
                                m.setType("teacher");
                                Log.d("StoryDebug", "Teacher story loaded: " + m.getStoryId());
                                if (now - m.getTimestamp() <= TWENTY_FOUR_HOURS) {
                                    synchronized (teachers) {
                                        teachers.add(m);
                                    }
                                }
                            }
                        }
                    }
                }
                if (done.incrementAndGet() >= STORY_READS) {
                    renderStories(college, teachers, onDone);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                if (done.incrementAndGet() >= STORY_READS) {
                    renderStories(college, teachers, onDone);
                }
            }
        };
        FirebaseDatabase.getInstance().getReference("stories/teachers").addValueEventListener(teacherStoriesListener);
    }

    private void renderStories(List<StoryModel> college, List<StoryModel> teachers, Runnable onDone) {
        if (!isAdded()) {
            onDone.run();
            return;
        }
        requireActivity().runOnUiThread(() -> {

            // ── 1. Sort college stories ASC (oldest first → plays in order) ───────
            Collections.sort(college, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

            // ── 2. Sort ALL teacher stories ASC first ─────────────────────────────
            Collections.sort(teachers, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

            // ── 3. Group teacher stories by teacherUid ────────────────────────────
            //    LinkedHashMap preserves the insertion order.
            Map<String, List<StoryModel>> teacherGroups = new LinkedHashMap<>();
            for (StoryModel s : teachers) {
                String uid = s.getTeacherUid() != null ? s.getTeacherUid() : "__unknown__";
                if (!teacherGroups.containsKey(uid)) {
                    teacherGroups.put(uid, new ArrayList<>());
                }
                teacherGroups.get(uid).add(s);
            }
            // Each group is already in ASC order because teachers was sorted ASC above.

            // ── 4. Build adapter ─────────────────────────────────────────────────
            // We now group ALL college stories into a single circle.
            Map<String, List<StoryModel>> finalGroups = new LinkedHashMap<>();

            if (isTeacher) {
                // Teachers see their own / other teachers' groups first, then college group
                finalGroups.putAll(teacherGroups);
                if (!college.isEmpty()) {
                    finalGroups.put("college_all", new ArrayList<>(college));
                }
            } else {
                // Students see the single college group first, then teacher groups
                if (!college.isEmpty()) {
                    finalGroups.put("college_all", new ArrayList<>(college));
                }
                finalGroups.putAll(teacherGroups);
            }

            // We no longer pass 'college' as a separate flat list. Everything goes in groups.
            StoryAdapter adapter = new StoryAdapter(requireContext(), finalGroups, isTeacher);

            rvStories.setAdapter(null);
            rvStories.setAdapter(adapter);
            adapter.notifyDataSetChanged();

            onDone.run();
        });
    }

    // ── Features ──────────────────────────────────────────────────────────
    private void loadFeatures(Runnable onDone) {
        FirebaseDatabase.getInstance().getReference("features").child("cmcs")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            FeatureModel initial = new FeatureModel("CMCS", "system", System.currentTimeMillis());
                            FirebaseDatabase.getInstance().getReference("features").child("cmcs")
                                    .setValue(initial)
                                    .addOnCompleteListener(t -> loadFeaturesData(onDone));
                        } else {
                            loadFeaturesData(onDone);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        loadFeaturesData(onDone);
                    }
                });
    }

    private void loadFeaturesData(Runnable onDone) {
        featuresRef = FirebaseDatabase.getInstance().getReference("features");
        featuresListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<FeatureModel> rawList = new ArrayList<>();
                FeatureModel cmcsModel = null;

                for (DataSnapshot child : snapshot.getChildren()) {
                    FeatureModel f = child.getValue(FeatureModel.class);
                    if (f == null) {
                        continue;
                    }
                    f.setFeatureId(child.getKey());
                    boolean hasSlides = child.child("slides").exists();

                    if ("cmcs".equals(child.getKey())) {
                        cmcsModel = f;
                    } else {
                        if (isTeacher || hasSlides) {
                            rawList.add(f);
                        }
                    }
                }

                List<FeatureModel> finalList = new ArrayList<>();
                if (cmcsModel != null) {
                    finalList.add(cmcsModel);
                }

                Collections.sort(rawList, (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

                finalList.addAll(rawList);

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> renderFeatures(finalList, snapshot.getChildrenCount(), onDone));
                } else {
                    onDone.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                onDone.run();
            }
        };
        featuresRef.addValueEventListener(featuresListener);
    }

    private void renderFeatures(List<FeatureModel> list, long totalCount, Runnable onDone) {
        if (!isAdded()) {
            onDone.run();
            return;
        }

        if (list.isEmpty() && !isTeacher) {
            layoutFeaturesSection.setVisibility(View.GONE);
            onDone.run();
            return;
        }

        layoutFeaturesSection.setVisibility(View.VISIBLE);

        FeatureAdapter adapter = new FeatureAdapter(requireContext(), list, isTeacher, this::loadAllSections);
        rvFeatures.setAdapter(null);
        rvFeatures.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        if (isTeacher && totalCount < 5) {
            btnAddFeature.setVisibility(View.VISIBLE);
        } else {
            btnAddFeature.setVisibility(View.GONE);
        }

        onDone.run();
    }

    // ── Highlights ────────────────────────────────────────────────────────
    private void loadHighlights(Runnable onDone) {
        FirebaseDatabase.getInstance().getReference("highlights")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<HighlightModel> list = new ArrayList<>();

                        // For each highlight, grab metadata + first media item for cover
                        for (DataSnapshot hlSnap : snapshot.getChildren()) {
                            HighlightModel hl = hlSnap.getValue(HighlightModel.class);
                            if (hl == null) {
                                continue;
                            }
                            hl.setHighlightId(hlSnap.getKey());

                            // Grab cover URL from first media child
                            DataSnapshot media = hlSnap.child("media");
                            String coverUrl = null;
                            long firstTs = Long.MAX_VALUE;
                            for (DataSnapshot mSnap : media.getChildren()) {
                                Long ts = mSnap.child("timestamp").getValue(Long.class);
                                if (ts != null && ts < firstTs) {
                                    firstTs = ts;
                                    coverUrl = mSnap.child("mediaUrl").getValue(String.class);
                                }
                            }
                            hl.setCoverUrl(coverUrl);
                            list.add(hl);
                        }
                        Collections.sort(list,
                                (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                        if (isAdded()) {
                            requireActivity().runOnUiThread(() -> renderHighlights(list, onDone));
                        } else {
                            onDone.run();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError e) {
                        onDone.run();
                    }
                });
    }

    private void renderHighlights(List<HighlightModel> list, Runnable onDone) {
        if (!isAdded()) {
            onDone.run();
            return;
        }
        if (list.isEmpty() && !isTeacher) {
            layoutHighlightsSection.setVisibility(View.GONE);
            onDone.run();
            return;
        }
        layoutHighlightsSection.setVisibility(View.VISIBLE);
        rvHighlights.setAdapter(new HighlightAdapter(requireContext(), list, isTeacher));
        onDone.run();
    }

    // ── Posts ─────────────────────────────────────────────────────────────
    private void loadPosts(Runnable onDone) {
        if (postsListener != null) {
            FirebaseDatabase.getInstance().getReference("posts").removeEventListener(postsListener);
        }
        postsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                postList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    PostModel p = ds.getValue(PostModel.class);
                    if (p != null) {
                        p.setPostId(ds.getKey());
                        postList.add(p);
                    }
                }
                Collections.sort(postList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> renderPosts(onDone));
                } else {
                    onDone.run();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onDone.run();
            }
        };
        FirebaseDatabase.getInstance().getReference("posts").addValueEventListener(postsListener);
    }

    private void renderPosts(Runnable onDone) {
        if (!isAdded()) {
            onDone.run();
            return;
        }

        if (postList.isEmpty() && !isTeacher) {
            layoutPostsSection.setVisibility(View.GONE);
            onDone.run();
            return;
        }
        layoutPostsSection.setVisibility(View.VISIBLE);

        postFeedAdapter = new PostFeedAdapter(requireContext(), postList, isTeacher);
        postGridAdapter = new PostGridAdapter(requireContext(), postList, isTeacher);

        if (isPostListView) {
            if (rvPosts.getAdapter() != postFeedAdapter) {
                rvPosts.setAdapter(postFeedAdapter);
            } else {
                postFeedAdapter.notifyDataSetChanged();
            }
            rvPosts.postDelayed(() -> {
                if (isPostListView && postFeedAdapter != null && postFeedLayoutManager != null) {
                    int fullyVisibleItem = postFeedLayoutManager.findFirstCompletelyVisibleItemPosition();
                    int firstVisible = postFeedLayoutManager.findFirstVisibleItemPosition();
                    if (fullyVisibleItem >= 0) {
                        postFeedAdapter.playVideoAtPosition(fullyVisibleItem, rvPosts);
                    } else if (firstVisible >= 0) {
                        postFeedAdapter.playVideoAtPosition(firstVisible, rvPosts);
                    }
                }
            }, 500);
        } else {
            if (rvPosts.getAdapter() != postGridAdapter) {
                rvPosts.setAdapter(postGridAdapter);
            } else {
                postGridAdapter.notifyDataSetChanged();
            }
        }

        onDone.run();
    }
}
