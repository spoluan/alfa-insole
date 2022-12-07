package com.alfaloop.insoleble.fragment;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alfaloop.insoleble.MainActivity;
import com.alfaloop.insoleble.R;
import com.alfaloop.insoleble.visualization.FootPressureView;
import com.alfaloop.insoleble.visualization.InsoleSensor;
import com.alfaloop.insoleble.visualization.SensorDataGetter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ConnectedFragment extends Fragment {
    private static final String TAG = ConnectedFragment.class.getSimpleName();

    private Context mainContext = null;
    private Handler handler = null;
    private TextView lDevNameView = null;
    private TextView lPressureView = null;
    private TextView lGyroView = null;
    private TextView rDevNameView = null;
    private TextView rPressureView = null;
    private TextView rGyroView = null;
    public static EditText etName = null;
    private String[] devicesName = null;
    private SensorDataGetter leftSensorDataGetter = null;
    private SensorDataGetter rightSensorDataGetter = null;
    private boolean switchSide = false;
    public static boolean record = false;
    private String getName = "";

    public static String getHerokuStatus = "";

    public static ConnectedFragment newInstance(String[] devicesName) {
        ConnectedFragment fragment = new ConnectedFragment();
        Bundle args = new Bundle();
        args.putStringArray("names", devicesName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        devicesName = getArguments().getStringArray("names");
        switchSide = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.connected_couple_view_fragment, parent, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        lDevNameView = (TextView)view.findViewById(R.id.c_left_device_name);
        lPressureView = (TextView)view.findViewById(R.id.c_left_pressure);
        lGyroView = (TextView)view.findViewById(R.id.c_left_gyro);
        rDevNameView = (TextView)view.findViewById(R.id.c_right_device_name);
        rPressureView = (TextView)view.findViewById(R.id.c_right_pressure);
        rGyroView = (TextView)view.findViewById(R.id.c_right_gyro);
        etName = (EditText) view.findViewById(R.id.etName);


        lDevNameView.setText(devicesName[0]);
        if(devicesName.length >1)
            rDevNameView.setText(devicesName[1]);

        InsoleSensor.loadData(getResources(), 0.6f, 1.2f);
        LinearLayout layout = (LinearLayout) view.findViewById(R.id.pressure_sensor_vis_view);
        leftSensorDataGetter = new SensorDataGetter(SensorDataGetter.Direction.LEFT, false);
        rightSensorDataGetter = new SensorDataGetter(SensorDataGetter.Direction.RIGHT, false);
        FootPressureView leftView = new FootPressureView(mainContext, leftSensorDataGetter);
        FootPressureView rightView = new FootPressureView(mainContext, rightSensorDataGetter);

        leftView.setLayoutParams(
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        rightView.setLayoutParams(
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        leftView.setOnClickListener(pressureViewClickListener);
        rightView.setOnClickListener(pressureViewClickListener);
        layout.addView(leftView);
        layout.addView(rightView);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mainContext = context;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    public void updateSensorView(byte deviceType, byte side, int[] pressure, float[] accel, float[] gyro, String currentDateTimeString) {

        //ConnectedFragment.etName.setEnabled(false); //Lock
        if(MainActivity.recordingButton.getText().toString().toLowerCase().equals("record")) {
            etName.setEnabled(true);
        }else{
            etName.setEnabled(false);
        }

        String pressureStr = null;
        String gyroStr = null;
        float[] data = null;

        if(pressure != null) {
            for (int i = 0; i < pressure.length; i++)
                if (pressure[i] < 3) pressure[i] = 0;
        }
        if(pressure != null && accel != null) {
            data = new float[]{accel[0], accel[1], accel[2],
                    (pressure[0]), (pressure[1]),
                    (pressure[2]), (pressure[3])};
        } else {
            if(pressure == null && accel != null)
                data = new float[]{accel[0], accel[1], accel[2],
                        0, 0, 0, 0};
            else if(pressure != null && accel == null)
                data = new float[]{0, 0, 0,
                        (pressure[0]), (pressure[1]),
                        (pressure[2]), (pressure[3])};
        }

        if(pressure != null)
            pressureStr = String.format("%d %d %d %d", pressure[0], pressure[1], pressure[2], pressure[3]);
        if(gyro != null)
            gyroStr = String.format("x: %.2f\ny: %.2f\nz: %.2f", gyro[0], gyro[1], gyro[2]);

        if((!switchSide && side == 0) || (switchSide && side == 1)) {
            if(gyroStr != null)
                lGyroView.setText(gyroStr);
            if(pressureStr != null)
                lPressureView.setText(pressureStr);
                if((pressure[0] != 0 || pressure[1] != 0 || pressure[2] != 0 || pressure[3] != 0) && record == true) {
                    mqttPublish(pressure, currentDateTimeString, "LEFT");
//                    heroku(pressure, currentDateTimeString, "LEFT");
//                    addErrorLogCat("LEFT", pressureStr + "; " + currentDateTimeString);
                }
            if(data != null)
                leftSensorDataGetter.addSensorData(data);
        } else if((!switchSide && side == 1) || (switchSide && side == 0)) {
            if(gyroStr != null)
                rGyroView.setText(gyroStr);
            if(pressureStr != null)
                rPressureView.setText(pressureStr);
            if((pressure[0] != 0 || pressure[1] != 0 || pressure[2] != 0 || pressure[3] != 0) && record == true) {
                addErrorLogCat("TIME NOW", currentDateTimeString);
                mqttPublish(pressure, currentDateTimeString, "RIGHT");
//                heroku(pressure, currentDateTimeString, "RIGHT");
//                addErrorLogCat("RIGHT", pressureStr + "; " + currentDateTimeString);
            }
            if(data != null)
                rightSensorDataGetter.addSensorData(data);
        }
    }

    private int R_HEEL = 0;
    private int R_THUMB = 0;
    private int R_INNER_BALL = 0;
    private int R_OUTER_BALL = 0;

    private int L_HEEL = 0;
    private int L_THUMB = 0;
    private int L_INNER_BALL = 0;
    private int L_OUTER_BALL = 0;

    private int status_r = 1;
    private int status_l = 1;



    private void mqttPublish(int[] pressure, String time, String status) {
        if(MainActivity.client != null) {

            String[] split = time.split(" ")[0].split(":");
            String hours = split[0];
            String minutes = split[1];
            String seconds = split[2];
            time = hours + ":" + minutes + ":" + seconds;

            if(status.toUpperCase().equals("RIGHT")) {
                R_HEEL = pressure[0];
                R_THUMB = pressure[1];
                R_INNER_BALL = pressure[2];
                R_OUTER_BALL = pressure[3];
                status_r = 0;
                addErrorLogCat("TIME", time + " RIGHT");
            }
            if(status.toUpperCase().equals("LEFT")) {
                L_HEEL = pressure[0];
                L_THUMB = pressure[1];
                L_INNER_BALL = pressure[2];
                L_OUTER_BALL = pressure[3];
                status_l = 0;
            }

            if(status_l == 0 || status_r == 0){
                new MQTTMaster(R_HEEL, R_THUMB, R_INNER_BALL, R_OUTER_BALL, L_HEEL, L_THUMB, L_INNER_BALL, L_OUTER_BALL, time);

                status_r = 1;
                status_l = 1;
//                addErrorLogCat("TIME", time + " LEFT");
//                showMessage("From Heroku: " + getHerokuStatus);
            }
        }
    }

    private void heroku(int[] pressure, String time, String status){
        ConnectivityManager connMgr = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        boolean isConnected = false;
        if (networkInfo != null && (isConnected = networkInfo.isConnected())) {
//            addErrorLogCat("DATA", status + "; " + time + "; " + pressure[0]);
//            decideStanding(time, status, pressure[0], pressure[1], pressure[2], pressure[3]);

            String[] split = time.split(" ")[0].split(":");
            String hours = split[0];
            String minutes = split[1];
            String seconds = split[2];
            time = hours + ":" + minutes + ":" + seconds;

            if(status.toUpperCase().equals("RIGHT")) {
                R_HEEL = pressure[0];
                R_THUMB = pressure[1];
                R_INNER_BALL = pressure[2];
                R_OUTER_BALL = pressure[3];
                status_r = 0;
                addErrorLogCat("TIME", time + " RIGHT");
            }
            if(status.toUpperCase().equals("LEFT")) {
                L_HEEL = pressure[0];
                L_THUMB = pressure[1];
                L_INNER_BALL = pressure[2];
                L_OUTER_BALL = pressure[3];
                status_l = 0;
            }

            if(status_l == 0 || status_r == 0){
                new Heroku(R_HEEL, R_THUMB, R_INNER_BALL, R_OUTER_BALL, L_HEEL, L_THUMB, L_INNER_BALL, L_OUTER_BALL, time);
                status_r = 1;
                status_l = 1;
                addErrorLogCat("TIME", time + " LEFT");
//                showMessage("From Heroku: " + getHerokuStatus);
            }
        }else{
            showMessage("Check your internet connection.");
            addErrorLogCat("Internet info", "No internet connection.");
        }
    }

    public static int duration = 20;

    int l_count = 0;
    int r_count = 0;
    List<String> tempSecond = new ArrayList<String>(); // thumb, outerBall, innerBall, heel
    private void decideStanding(String time, String status, int thumb, int outer_ball, int inner_ball, int heel){
//        addErrorLogCat("Datas", time + status + thumb + outer_ball);
        //if(duration > 0){
        String[] split = time.split(" ")[0].split(":");
        String hours = split[0];
        String minutes = split[1];
        String seconds = split[2];

        if(Arrays.asList(tempSecond.toArray(new String[tempSecond.size()])).contains(seconds) || tempSecond.size() == 0){
            tempSecond.add(seconds);

            if(status.toUpperCase().equals("RIGHT")) {
                R_HEEL += heel;
                R_THUMB += thumb;
                R_INNER_BALL += inner_ball;
                R_OUTER_BALL += outer_ball;
//                    addErrorLogCat("SEND", status + "; " + r_count + "; " + seconds + "; " + R_HEEL + " " + R_THUMB + " " + R_INNER_BALL + " " + R_OUTER_BALL);
                r_count += 1;
            }
            if(status.toUpperCase().equals("LEFT")) {
                L_HEEL += heel;
                L_THUMB += thumb;
                L_INNER_BALL += inner_ball;
                L_OUTER_BALL += outer_ball;
//                    addErrorLogCat("SEND", status + "; " + l_count + "; " + seconds + "; " + L_HEEL + " " + L_THUMB + " " + L_INNER_BALL + " " + L_OUTER_BALL);
                l_count += 1;
            }
        }else{
            try {
                try {
                    int l_total = (L_HEEL + L_THUMB + L_INNER_BALL + L_OUTER_BALL) / tempSecond.size();
                    int r_total = (R_HEEL + R_THUMB + R_INNER_BALL + R_OUTER_BALL) / tempSecond.size();

                    time = hours + ":" + minutes + ":" + seconds;
//                        addErrorLogCat("TOTAL", time + "; " + l_total + "; " + r_total);
                    new Heroku(R_HEEL / tempSecond.size(), R_THUMB / tempSecond.size(), R_INNER_BALL / tempSecond.size(), R_OUTER_BALL / tempSecond.size(), L_HEEL / tempSecond.size(), L_THUMB / tempSecond.size(), L_INNER_BALL / tempSecond.size(), L_OUTER_BALL / tempSecond.size(), time);
                }catch (Exception e){}
                // duration -= 1;
            } catch (Exception ex){
                showMessage("Issues:" + ex.getMessage());
                addErrorLogCat("SECONDS", ex.getMessage());
            }
            R_HEEL = 0;
            R_THUMB = 0;
            R_INNER_BALL = 0;
            R_OUTER_BALL = 0;

            L_HEEL = 0;
            L_THUMB = 0;
            L_INNER_BALL = 0;
            L_OUTER_BALL = 0;
            tempSecond.clear();
        }
       // }

//        if(duration == 0){
//            duration -= 1;
//            MainActivity.stopRecording();
//        }
    }

    public void showMessage(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
    }

    public void showMessageLong(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
    }

    public String convertStreamtoString(InputStream is) {
        String line = "";
        String data = "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                data += line;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonParser jp = new JsonParser();
        JsonElement je = jp.parse(data);

        String prettyJsonString = gson.toJson(je);
        prettyJsonString = prettyJsonString.replace("\\u0027", "");

        return prettyJsonString; //je.getAsJsonObject().get("response").toString();
    }

    private void addErrorLogCat(String tag, String message){
        Log.e(tag, message);
    }

    public void updatePowerView(byte side, int batteryPower) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (batteryPower > 0)
                        if (side == 0) {
                            lGyroView.setText(String.format("Power %d %%", batteryPower));
                        } else if (side == 1) {
                            rGyroView.setText(String.format("Power %d %%", batteryPower));
                        }
                } catch(NullPointerException npe) {
                    npe.printStackTrace();
                }
            }
        }, 1000);
    }

    private View.OnClickListener pressureViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switchSide = !switchSide;
            if(handler != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String tmp = lDevNameView.getText().toString();
                        lDevNameView.setText(rDevNameView.getText().toString());
                        rDevNameView.setText(tmp);

                        tmp = lPressureView.getText().toString();
                        lPressureView.setText(rPressureView.getText().toString());
                        rPressureView.setText(tmp);

                        tmp = lGyroView.getText().toString();
                        lGyroView.setText(rGyroView.getText().toString());
                        rGyroView.setText(tmp);

                        Toast.makeText(mainContext, R.string.pressure_side_switch_msg,
                                Toast.LENGTH_SHORT).show();
                    }
                });
        }
    };

    class MQTTMaster implements Runnable{

        Thread t;
        int R_HEEL; int R_THUMB; int R_INNER_BALL; int R_OUTER_BALL; int L_HEEL; int L_THUMB; int L_INNER_BALL; int L_OUTER_BALL; String time;
        MQTTMaster(int R_HEEL, int R_THUMB, int R_INNER_BALL, int R_OUTER_BALL, int L_HEEL, int L_THUMB, int L_INNER_BALL, int L_OUTER_BALL, String time){
            this.R_HEEL = R_HEEL; this.R_THUMB = R_THUMB; this.R_INNER_BALL = R_INNER_BALL; this.R_OUTER_BALL = R_OUTER_BALL; this.L_HEEL = L_HEEL; this.L_THUMB = L_THUMB; this.L_INNER_BALL = L_INNER_BALL; this.L_OUTER_BALL = L_OUTER_BALL; this.time = time;
            t = new Thread(this, "MQTT");
            t.start();
        }
        public void run() {
            JSONObject json = new JSONObject();

            getName = etName.getText().toString();
            if(getName.equals("")){
                getName = "SEVENDI";
            }
            try {
                json.accumulate("R_HEEL", this.R_HEEL);
                json.accumulate("R_THUMB", this.R_THUMB);
                json.accumulate("R_INNER_BALL", this.R_INNER_BALL);
                json.accumulate("R_OUTER_BALL", this.R_OUTER_BALL);
                json.accumulate("L_HEEL", this.L_HEEL);
                json.accumulate("L_THUMB", this.L_THUMB);
                json.accumulate("L_INNER_BALL", this.L_INNER_BALL);
                json.accumulate("L_OUTER_BALL", this.L_OUTER_BALL);
                json.accumulate("TIME", this.time);
                json.accumulate("NAME", getName); // TAG_NAME

            } catch (Exception ex) {
                addErrorLogCat("JSON Object", ex.getMessage() + "");
            }

            addErrorLogCat("Current json", json.toString());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonParser jp = new JsonParser();
            JsonElement je = jp.parse(json.toString());

            String prettyJsonString = gson.toJson(je);
            addErrorLogCat("Pretty JSON", prettyJsonString);

            try {
                String topic = "/sevendi/smartinsoles";
                String payload = json.toString();
                byte[] encodedPayload = new byte[0];
                try {
                    encodedPayload = payload.getBytes("UTF-8");
                    MqttMessage message = new MqttMessage(encodedPayload);
                    message.setRetained(true);
                    MainActivity.client.publish(topic, message);
                } catch (UnsupportedEncodingException | MqttException e) {
                    e.printStackTrace();
                }
            } catch (Exception ex) {
                addErrorLogCat("Connection error", ex.getMessage() + "");
            }
        }
    }

    class Heroku implements Runnable {

        Thread t;
        int R_HEEL; int R_THUMB; int R_INNER_BALL; int R_OUTER_BALL; int L_HEEL; int L_THUMB; int L_INNER_BALL; int L_OUTER_BALL; String time;
        Heroku(int R_HEEL, int R_THUMB, int R_INNER_BALL, int R_OUTER_BALL, int L_HEEL, int L_THUMB, int L_INNER_BALL, int L_OUTER_BALL, String time){
            this.R_HEEL = R_HEEL; this.R_THUMB = R_THUMB; this.R_INNER_BALL = R_INNER_BALL; this.R_OUTER_BALL = R_OUTER_BALL; this.L_HEEL = L_HEEL; this.L_THUMB = L_THUMB; this.L_INNER_BALL = L_INNER_BALL; this.L_OUTER_BALL = L_OUTER_BALL; this.time = time;
            t = new Thread(this, "Heroku");
            t.start();
        }
        public void run(){
            String responseContent = "";
            URL url = null;
            try {
                String WSDL = "https://insoles.herokuapp.com/req";
                url = new URL(WSDL);
                addErrorLogCat("URL", url + "");
            } catch (Exception ex) {
                addErrorLogCat("URL", ex.getMessage() + "");
            }
            JSONObject json = new JSONObject();

            getName = etName.getText().toString();
            if(getName.equals("")){
                getName = "SEVENDI";
            }
            try {
                json.accumulate("METHOD", "INSERT");
                json.accumulate("R_HEEL", this.R_HEEL);
                json.accumulate("R_THUMB", this.R_THUMB);
                json.accumulate("R_INNER_BALL", this.R_INNER_BALL);
                json.accumulate("R_OUTER_BALL", this.R_OUTER_BALL);
                json.accumulate("L_HEEL", this.L_HEEL);
                json.accumulate("L_THUMB", this.L_THUMB);
                json.accumulate("L_INNER_BALL", this.L_INNER_BALL);
                json.accumulate("L_OUTER_BALL", this.L_OUTER_BALL);
                json.accumulate("TIME", this.time);
                json.accumulate("NAME", getName); // TAG_NAME

            } catch (Exception ex) {
                addErrorLogCat("JSON Object", ex.getMessage() + "");
            }

            addErrorLogCat("Current json", json.toString());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonParser jp = new JsonParser();
            JsonElement je = jp.parse(json.toString());

            String prettyJsonString = gson.toJson(je);
            addErrorLogCat("Pretty JSON", prettyJsonString);

            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
                conn.setDoOutput(true);
                conn.setDoInput(true);

                addErrorLogCat("Connection", conn + "");

                conn.connect();
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(json.toString());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                addErrorLogCat("Response code", responseCode + "");

                String responseMessage = conn.getResponseMessage();
                addErrorLogCat("Response message", responseMessage + "");

                responseContent = convertStreamtoString((InputStream) conn.getContent());
                addErrorLogCat("Response", responseContent + "");

                conn.disconnect();

            } catch (Exception ex) {
                addErrorLogCat("Connection error", ex.getMessage() + "");
            }
        }
    }
}
