package com.otatakashi.weatherchecker;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    /**位置情報関係のPermission取得の為のREQUEST_CODE*/
    private static final int REQUEST_CODE = 1000;
    //OpenWeatherのURL
    private static final String WEATHER_INFO_URL ="https://api.openweathermap.org/data/2.5/weather?lang=ja";
    private static final String Q = "&q=";
    //天気APIのkey
    private static final String APP_ID = "e362b5a43216519be3b9760f7c427849";
    //city
    private static final String CITY_NAME = "Nishinomiya";
    //UI系
    private TextView mWeatherText;
    private Spinner mCitySpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //LocationPermissionの要求
        checkLocationPermission();
        setSpinnerList();

        findViewById(R.id.getCityInfoBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String selectingCity = (String) mCitySpinner.getSelectedItem();
                String fullUrl = WEATHER_INFO_URL + Q + selectingCity + "&APPID=" + APP_ID;
                Log.d(TAG, fullUrl);
                receiverWeatherInfo(fullUrl);
            }
        });
    }

    /**
     * Permission要求
     */
    private void checkLocationPermission() {
        //権限を保持してるかの確認
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
         || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            //ACCESS_FINE_LOCATION もしくは ACCESS_COARSE_LOCATIONを既に取得済み
            Log.d(TAG, "already get permission with ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION");
        } else{
            //保持してない時はrequestPermissionを行う
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Dialogの結果がPermission取得済み

                } else {
                    //UserがPermissionを拒否した時
                }
                break;
        }
    }

    private void setSpinnerList() {
        mCitySpinner = findViewById(R.id.chooseCity);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.city_list, android.R.layout.simple_spinner_dropdown_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mCitySpinner.setAdapter(adapter);
    }

    /**
     * 天気情報をバックグランドで取得する為のHandler登録処理
     * @param url OpenWeatherに接続する為のUrl
     */
    private void receiverWeatherInfo(final String url) {
        Looper mainLooper = Looper.getMainLooper();
        Handler handler = HandlerCompat.createAsync(mainLooper);
        WeatherInfoBackgroundReceiver backgroundReceiver =
                new WeatherInfoBackgroundReceiver(handler, url);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(backgroundReceiver);
    }

    /**
     * OpenWeatherMapからバックグランドで情報取得するClass
     */
    private class WeatherInfoBackgroundReceiver implements Runnable{
        private final String TAG_BACKGROUND_WEATHER_INFO = WeatherInfoBackgroundReceiver.class.getSimpleName();
        /*Handlerオブジェクト*/
        private final Handler _handler;
        //お天気情報を取得するURL
        private final String _url;

        public WeatherInfoBackgroundReceiver(final Handler handler, final String url) {
            this._handler = handler;
            this._url = url;
        }

        @WorkerThread
        @Override
        public void run() {
            //Web APIを利用して情報取得を開始する
            Log.d(TAG_BACKGROUND_WEATHER_INFO, "start HTTP connection");
            HttpURLConnection con = null;           //
            InputStream input = null;               //レスポンスの結果のInputStreamオブジェクト
            String result = null;                   //JSONデータ
            try {
                URL url = new URL(_url);
                con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(1000);        //接続に使える時間
                con.setReadTimeout(1000);           //データ取得に使える時間
                con.setRequestMethod("GET");        //取得にMethodを指定
                con.connect();                      //接続
                input = con.getInputStream();       //レスポンスデータ取得
                //inputStreamを文字列変換
                result = changeInputStreamToString(input);
                Log.d(TAG_BACKGROUND_WEATHER_INFO, "Success getting WeatherInfo. Result:" + result);
            } catch (MalformedURLException e) {
                Log.w(TAG, "URL変換失敗");
                e.printStackTrace();
            } catch (SocketTimeoutException e) {
                Log.w(TAG, "通信タイムアウト");
                e.printStackTrace();
            } catch (IOException e) {
                Log.w(TAG, "通信失敗");
                e.printStackTrace();
            } finally {
                //HttpURLConnectionオブジェクトがnullでないなら解放
                if (con != null) {
                    con.disconnect();
                }
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        Log.w(TAG, "InputStream解放失敗");
                    }
                }
            }
            WeatherInfoPostExecutor postExecutor = new WeatherInfoPostExecutor(result);
            _handler.post(postExecutor);
        }

        /**
         * 取得したInputStream型の情報をString型変換間
         * @param inputStream HTTP接続から取得したデータ
         * @return 文字列データ
         * @throws IOException
         */
        private String changeInputStreamToString(final InputStream inputStream) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuffer stringBuffer = new StringBuffer();
            char[] c_buffer = new char[1024];
            int line;
            while (0 <= (line = reader.read(c_buffer))) {
                stringBuffer.append(c_buffer, 0, line);
            }
            return stringBuffer.toString();
        }
    }

    /**
     * WeatherInfoBackgroundReceiverから情報を受け取り更新するHandlerクラス
     */
    private class WeatherInfoPostExecutor implements Runnable {
        private final String TAG_POST = WeatherInfoPostExecutor.class.getSimpleName();
        //取得したJsonデータの文字列
        private final String json_result;

        //コンストラクタ
        public WeatherInfoPostExecutor(final String result) {
            this.json_result = result;
        }

        @UiThread
        @Override
        public void run() {
            //uiスレッドで行うクラス
            String cityName = null;                    //都市名
            String weather = null;                     //天気
            try {
                JSONObject rootJSON = new JSONObject(json_result);
                cityName = rootJSON.getString("name");

                JSONArray weatherJSONArray = rootJSON.getJSONArray("weather");
                JSONObject weatherJSON = weatherJSONArray.getJSONObject(0);
                weather = weatherJSON.getString("description");

            } catch (JSONException e) {
                Log.w(TAG_POST, "JSON解析失敗");
                e.printStackTrace();
            }
            TextView chooseCity = findViewById(R.id.choseCity);
            TextView weatherResult = findViewById(R.id.weatherResult);
            chooseCity.setText(cityName);
            weatherResult.setText(weather);
        }
    }
}