package com.kantoniak.greendeer;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.DatePicker;

import com.google.protobuf.InvalidProtocolBufferException;
import com.kantoniak.greendeer.data.DataProvider;
import com.kantoniak.greendeer.proto.Run;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.StatusRuntimeException;

public class AddRunActivity extends AppCompatActivity implements RunDetailsFragment.OnFragmentInteractionListener, DatePickerDialog.OnDateSetListener {

        final static String EXTRA_RUN = "extra-run";

    private class NetworkUpdatesHandler extends Handler {
        NetworkUpdatesHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg){
            switch (msg.what) {
                case MESSAGE_ADD_RUNS:
                    // TODO(krzysztofa): UI information, change button to spinner, handle errors
                    Run toAdd = (Run) msg.obj;
                    logger.log(Level.INFO, "Saving run...");
                    try {
                        dataProvider.addRun(toAdd);

                        // Inform HomeActivity about success
                        Intent intent = new Intent();
                        setResult(RESULT_OK, intent);
                        finish();

                    } catch (StatusRuntimeException e) {
                        logger.log(Level.WARNING, "RPC failed: " + e.getStatus().getCode());
                    }
                    break;

                case MESSAGE_EDIT_RUNS:
                    // TODO(krzysztofa): UI information, change button to spinner, handle errors
                    Run toEdit = (Run) msg.obj;
                    logger.log(Level.INFO, "Saving updated run...");
                    try {
                        dataProvider.editRun(toEdit);

                        // Inform HomeActivity about success
                        Intent intent = new Intent();
                        setResult(RESULT_OK, intent);
                        finish();

                    } catch (StatusRuntimeException e) {
                        logger.log(Level.WARNING, "RPC failed: " + e.getStatus().getCode());
                    }
                    break;
            }
        }
    };

    private static final Logger logger = Logger.getLogger(AddRunActivity.class.getName());
    private static final int MESSAGE_ADD_RUNS = 1000;
    private static final int MESSAGE_EDIT_RUNS = 1001;

    private final DataProvider dataProvider = new DataProvider();
    private final HandlerThread networkUpdatesThread = new HandlerThread("NetworkUpdatesThread");
    private Handler networkUpdatesHandler;

    private boolean editMode = false;
    private RunDetailsFragment mRunDetailsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_run);

        Intent intent = getIntent();
        this.editMode = intent.hasExtra(EXTRA_RUN);

        mRunDetailsFragment = (RunDetailsFragment) getFragmentManager().findFragmentById(R.id.details_fragment);

        if (editMode) {
            this.setTitle(R.string.edit_run);
            try {
                mRunDetailsFragment.setFromRun(Run.parseFrom(intent.getByteArrayExtra(EXTRA_RUN)));
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        } else {
            this.setTitle(R.string.add_run);
        }

        networkUpdatesThread.start();
        networkUpdatesHandler = new NetworkUpdatesHandler(networkUpdatesThread.getLooper());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_add_run, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_save_run) {
            if (!mRunDetailsFragment.runValidation()) {
                return true;
            }
            if (editMode) {
                networkUpdatesHandler.sendMessage(
                        networkUpdatesHandler.obtainMessage(MESSAGE_EDIT_RUNS, mRunDetailsFragment.getEditedRun()));
            } else {
                networkUpdatesHandler.sendMessage(
                        networkUpdatesHandler.obtainMessage(MESSAGE_ADD_RUNS, mRunDetailsFragment.getEditedRun()));
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int day) {
        Calendar cal = new GregorianCalendar(year, month, day);
        mRunDetailsFragment.setDate(cal);
    }
}
