package com.example.heartrate;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.components.YAxis;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.content.Intent;

public class MainActivity extends AppCompatActivity {
    private LineChart heartRateChart;
    private float lastDataPoint = Float.MIN_VALUE;
    private float peakTimeValue = 0;
    private boolean isAboveThreshold = false;
    private final float R_PEAK_THRESHOLD = 600;
    private ArrayList<Float> recentECGData = new ArrayList<>();
    private volatile float mostRecentHeartRate = 0;
    private ArrayList<Float> rIntervals = new ArrayList<>();
    private float rLastPeak = 0;
    private float rInterval = 0;
    private TextView heartRateTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        heartRateTextView = findViewById(R.id.heartRateTextView);

        Button goToHomePageButton = findViewById(R.id.goToHomePageButton);
        goToHomePageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HomePageActivity.class));
            }

        });

        Button btnHeartRateAnalysis = findViewById(R.id.btnHeartRateAnalysis);
        btnHeartRateAnalysis.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HeartAnalysis.class);
            intent.putExtra("HeartRate", mostRecentHeartRate);
            startActivity(intent);
        });

        heartRateChart = findViewById(R.id.heartRateChart);
        setupChart();
        //loadHeartRateDataFromCSV(); // Load and plot the data
        // Start adding data dynamically
        //addDataDynamically();
        connectToServerAndReceiveData();
    }

    private float calculateCurrentHeartRate(ArrayList<Float> intervalList) {
        int start = Math.max(0, intervalList.size() - 5); // Ensure we don't go out of bounds
        List<Float> lastIntervals = intervalList.subList(start, intervalList.size());

        float sum = 0;
        for (float interval : lastIntervals) {
            sum += interval;
        }

        float averageInterval = sum / lastIntervals.size(); // Calculate average
        float bpm = 60000 / averageInterval; // Convert from ms to BPM

        // Log the average interval and the calculated BPM
        Log.d("HeartRate", "Average Interval: " + averageInterval);
        Log.d("HeartRate", "Calculated BPM: " + bpm);

        return bpm;
    }

    private boolean crossesThreshold(float ecgValue) {
        return ecgValue > R_PEAK_THRESHOLD;
    }

    private void setupChart() {
        Description description = new Description();
        description.setText("Heart Rate Data");
        heartRateChart.setDescription(description);
        heartRateChart.setTouchEnabled(true);
        heartRateChart.setDragEnabled(true);
        heartRateChart.setScaleEnabled(true);

        LineDataSet dataSet = new LineDataSet(new ArrayList<Entry>(), "Heart Rate");
        dataSet.setColor(android.graphics.Color.BLUE);
        dataSet.setValueTextColor(android.graphics.Color.BLACK);

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);

        LineData lineData = new LineData(dataSets);
        heartRateChart.setData(lineData);

        XAxis xAxis = heartRateChart.getXAxis();
        xAxis.enableGridDashedLine(10f, 10f, 10f);
        xAxis.setDrawGridLines(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10);
        xAxis.setTextColor(Color.BLACK);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setLabelCount(5);
        xAxis.setGranularity(1f);

    }

    //private volatile float mostRecentHeartRate = 0;

    private void connectToServerAndReceiveData() {
        new Thread(() -> {
            try {
                // Use 10.0.2.2 to access your host machine's localhost from the Android emulator
                Socket socket = new Socket("192.168.2.11", 65432);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                ArrayList<Float> recentECGData = new ArrayList<>();

                String line;
                int i = 0;
                while ((line = in.readLine()) != null) {
                    final String[] tokens = line.split(",");
                    if (tokens.length >= 2) {
                        try {
                            float timeValue = Float.parseFloat(tokens[1].trim());
                            float heartRateValue = Float.parseFloat(tokens[0].trim());
                            // Update the chart on the UI thread
                            float finalHeartRateValue = heartRateValue;
                            float finalHeartRateValue2 = finalHeartRateValue;
                            runOnUiThread(() -> addEntryToChart(timeValue, finalHeartRateValue2));

                            //find r peaks
                            if (crossesThreshold(lastDataPoint)){
                                if (heartRateValue < lastDataPoint && i == 0){
                                    rInterval = peakTimeValue - rLastPeak;
                                    rLastPeak = peakTimeValue;
                                    rIntervals.add(rInterval);
                                    i = 1;
                                }
                            }
                            else {
                                isAboveThreshold = false;
                                i = 0;
                            }

                            lastDataPoint =heartRateValue;
                            peakTimeValue = timeValue;

                            // add ecg value to recent ecg data
                            recentECGData.add(heartRateValue);
                            if (recentECGData.size() > 30) {
                                recentECGData.remove(0); // Keep recentECGData size manageable
                            }
                            // Calculate current heart rate
                            float currentHeartRate = calculateCurrentHeartRate(rIntervals);
                            Log.d("HeartRate", "Current Heart Rate: " + currentHeartRate);

                            // Update the chart and the TextView on the UI thread
                            finalHeartRateValue = heartRateValue;
                            float finalCurrentHeartRate = currentHeartRate;
                            float finalHeartRateValue1 = finalHeartRateValue;
                            runOnUiThread(() -> {
                                addEntryToChart(timeValue, finalHeartRateValue1);
                                heartRateTextView.setText(String.format(Locale.getDefault(), "Heart Rate: %.2f", finalCurrentHeartRate));
                            });

                        } catch (NumberFormatException e) {
                            Log.e("TCP", "Parsing error", e);
                        }
                    }
                }
                socket.close();
            } catch (IOException e) {
                Log.e("TCP", "Client error", e);
            }
        }).start();
    }



    private void addEntryToChart(float time, float heartRate) {
        LineData data = heartRateChart.getData();
        if (data != null) {
            ILineDataSet set = data.getDataSetByIndex(0);
            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }
            else{
                set.setDrawValues(false);
            }
            data.addEntry(new Entry(time, heartRate), 0);
            data.notifyDataChanged();

            heartRateChart.notifyDataSetChanged();
            //heartRateChart.moveViewToX(data.getEntryCount());
            //heartRateChart.invalidate(); // Refresh the chart
            // After adding data to the chart
            heartRateChart.setVisibleXRangeMaximum(1000); // Show only 10 entries at a time
            heartRateChart.moveViewToX(data.getXMax() - 10); // Move to the latest entry
            heartRateChart.invalidate(); // Refresh the chart

        }
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Heart Rate");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(android.graphics.Color.BLUE);
        set.setLineWidth(2f);
        set.setDrawCircles(false);
        set.setFillAlpha(65);
        set.setFillColor(android.graphics.Color.BLUE);
        set.setHighLightColor(android.graphics.Color.rgb(244, 117, 117));
        set.setValueTextColor(android.graphics.Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }
}