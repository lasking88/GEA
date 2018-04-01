package com.rug.gea.Client.building;

import com.rug.gea.DataModels.Data;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public abstract class Building {
    public interface OnDataReceivedListener {
        void onDataReceived(Data d) throws IOException, TimeoutException;
    }

    private Data[] data;

    public Data[] getData() {
        return data;
    }

    public abstract void addListener(OnDataReceivedListener listener);
}
