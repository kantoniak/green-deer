package com.kantoniak.greendeer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Toast;

import com.kantoniak.greendeer.data.DataProvider;
import com.kantoniak.greendeer.proto.Run;
import com.kantoniak.greendeer.proto.Stats;
import com.kantoniak.greendeer.ui.CompletnessIndicator;
import com.kantoniak.greendeer.ui.ItemTouchListener;
import com.kantoniak.greendeer.ui.RecyclerItemClickListener;
import com.kantoniak.greendeer.ui.RunAdapter;
import com.kantoniak.greendeer.ui.RunListItemInfo;

import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.StatusRuntimeException;

public class HomeActivity extends AppCompatActivity {

    private class NetworkUpdatesHandler extends Handler {
        NetworkUpdatesHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg){
            switch (msg.what) {
                case MESSAGE_FETCH_RUNS:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showFetchRunsLoadingBlock();
                        }
                    });
                    logger.log(Level.INFO, "Fetching runs...");
                    try {
                        final List<Run> runs = dataProvider.getListOfRuns();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                            runAdapter.updateRuns(runs);
                            showRunsRecyclerView();
                            }
                        });
                    } catch (StatusRuntimeException e) {
                        logger.log(Level.WARNING, "RPC failed: " + e.getStatus().getCode());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                            showFetchRunsFailedBlock();
                            }
                        });
                    }
                    break;
                case MESSAGE_FETCH_STATS:
                    logger.log(Level.INFO, "Fetching stats...");
                    try {
                        final Stats stats = dataProvider.getStats();
                        logger.log(Level.INFO, "RPC stats: " + stats);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mDistanceCompletness.setGoal(stats.getMetersGoal() / 1000.d);
                                mDistanceCompletness.setValue(stats.getMetersSum() / 1000.d);
                                mWeightCompletness.setGoal(stats.getWeightGoal());
                                mWeightCompletness.setValue(stats.getWeightLowest());
                            }
                        });
                    } catch (StatusRuntimeException e) {
                        logger.log(Level.WARNING, "RPC failed: " + e.getStatus().getCode());
                    }
                    break;
                case MESSAGE_DELETE_RUN:
                    logger.log(Level.INFO, "Fetching runs...");
                    try {
                        final int id = (int) msg.obj;
                        dataProvider.deleteRun(id);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getBaseContext(), "Deleted run #" + id, Toast.LENGTH_SHORT).show();
                            }
                        });
                        startFetchRuns();
                    } catch (StatusRuntimeException e) {
                        logger.log(Level.WARNING, "RPC failed: " + e.getStatus().getCode());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getBaseContext(), "Could not delete run", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                    break;
            }
        }
    };

    private static final Logger logger = Logger.getLogger(HomeActivity.class.getName());
    private static final int MESSAGE_FETCH_RUNS = 1000;
    private static final int MESSAGE_FETCH_STATS = 1001;
    private static final int MESSAGE_DELETE_RUN = 1002;
    private static final int RESULT_ADD_RUN = 1100;
    private static final int RESULT_EDIT_RUN = 1101;

    private final DataProvider dataProvider = new DataProvider();
    private RunAdapter runAdapter;

    private CompletnessIndicator mDistanceCompletness;
    private CompletnessIndicator mWeightCompletness;
    private View mRunsLoadingView;
    private View mRunsFailedView;
    private Button mRetryFetchRunsButton;
    private RecyclerView mRecyclerView;

    private final HandlerThread networkUpdatesThread = new HandlerThread("NetworkUpdatesThread");
    private Handler networkUpdatesHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setIcon(R.drawable.ic_deer);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addNewRun();
            }
        });

        this.mDistanceCompletness = (CompletnessIndicator) findViewById(R.id.distance_completness);
        this.mWeightCompletness = (CompletnessIndicator) findViewById(R.id.weight_completness);
        this.mRunsLoadingView = (View) findViewById(R.id.fetch_runs_loading_block);
        this.mRunsFailedView = (View) findViewById(R.id.fetch_runs_failed_block);
        this.mRetryFetchRunsButton = (Button) findViewById(R.id.retry_fetch_runs_button);

        mDistanceCompletness.setCategory("Distance");
        mDistanceCompletness.setValueSuffix(" km");
        mWeightCompletness.setCategory("Weight");
        mWeightCompletness.setValueSuffix(" kg");

        mRetryFetchRunsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startFetchRuns();
            }
        });
        setupRecyclerView();

        networkUpdatesThread.start();
        networkUpdatesHandler = new NetworkUpdatesHandler(networkUpdatesThread.getLooper());

        startFetchRuns();
    }

    private void setupRecyclerView() {
        runAdapter = new RunAdapter(getBaseContext());
        mRecyclerView = (RecyclerView) findViewById(R.id.run_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(runAdapter);
        mRecyclerView.setNestedScrollingEnabled(false);

        mRecyclerView.addOnItemTouchListener(new ItemTouchListener(this, mRecyclerView, new RecyclerItemClickListener() {
            @Override
            public void onClick(View view, int position) {
                logger.log(Level.INFO, "CLICK");
            }

            @Override
            public void onLongPress(View view, int position) {
                logger.log(Level.INFO, "LONG PRESS");
                openContextMenu(view);
            }
        }));

        registerForContextMenu(mRecyclerView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            startFetchRuns();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();

        RunListItemInfo menuInfo = (RunListItemInfo) item.getMenuInfo();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_edit) {
            editRun(menuInfo.run);
            return true;
        }

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_delete) {
            startDeleteRun(menuInfo.run.getId());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case RESULT_ADD_RUN:
            case RESULT_EDIT_RUN:
                if (resultCode == RESULT_OK) {
                    startFetchRuns();
                }
                break;
        }
    }

    private void startFetchRuns() {
        networkUpdatesHandler.sendMessage(
                networkUpdatesHandler.obtainMessage(MESSAGE_FETCH_STATS));
        networkUpdatesHandler.sendMessage(
                networkUpdatesHandler.obtainMessage(MESSAGE_FETCH_RUNS));
    }

    private void startDeleteRun(int id) {
        networkUpdatesHandler.sendMessage(
                networkUpdatesHandler.obtainMessage(MESSAGE_DELETE_RUN, id));
    }

    private void showFetchRunsLoadingBlock() {
        mRunsLoadingView.setVisibility(View.VISIBLE);
        mRunsFailedView.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.GONE);
    }

    private void showFetchRunsFailedBlock() {
        mRunsLoadingView.setVisibility(View.GONE);
        mRunsFailedView.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.GONE);
    }

    private void showRunsRecyclerView() {
        mRunsLoadingView.setVisibility(View.GONE);
        mRunsFailedView.setVisibility(View.GONE);
        mRecyclerView.setVisibility(View.VISIBLE);
    }

    private void addNewRun() {
        startActivityForResult(new Intent(this, AddRunActivity.class), RESULT_ADD_RUN);
    }

    private void editRun(Run run) {
        Intent intent = new Intent(this, AddRunActivity.class);
        intent.putExtra(AddRunActivity.EXTRA_RUN, run.toByteArray());
        startActivityForResult(intent, RESULT_EDIT_RUN);
    }
}
