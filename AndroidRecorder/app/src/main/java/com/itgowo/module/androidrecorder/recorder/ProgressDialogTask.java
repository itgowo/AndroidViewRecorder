package com.itgowo.module.androidrecorder.recorder;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;

public abstract class ProgressDialogTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    private int promptRes;
    private ProgressDialog mProgressDialog;
    private Context context;

    public ProgressDialogTask(int promptRes, Context context) {
        this.context = context;
        this.promptRes = promptRes;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        mProgressDialog = ProgressDialog.show(context, null, context.getString(promptRes), true);
    }

    @Override
    protected void onProgressUpdate(Progress... values) {
        super.onProgressUpdate(values);
//            mProgressDialog.setProgress(values[0]);
    }

    @Override
    protected void onPostExecute(Result result) {
        super.onPostExecute(result);
        mProgressDialog.dismiss();
    }
}