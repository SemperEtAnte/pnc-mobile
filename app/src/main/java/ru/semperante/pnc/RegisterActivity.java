package ru.semperante.pnc;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class RegisterActivity extends Activity {
    private RequestQueue rq;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);
        rq = Volley.newRequestQueue(this);
    }

    public void doRegister(View view) {
        EditText login = findViewById(R.id.login);
        EditText password = findViewById(R.id.password);
        String lg = login.getText().toString();
        String pg = password.getText().toString();
        if (lg == null || lg.length() == 0) {
            login.setHighlightColor(Color.RED);
            return;
        }
        if (pg == null || pg.length() < 3 || pg.length() > 16) {
            login.setHighlightColor(Color.RED);
        }
        JSONObject data = new JSONObject();
        try {
            data.put("login", lg);
            data.put("password", pg);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jor = new JsonObjectRequest(Request.Method.POST, MainActivity.HEROKU_URL+"/v1/user/register", data, response -> {
            try {
                System.out.println("Responded 200 or 201");
                SharedPreferences sp = getSharedPreferences("AUTHORIZATION", MODE_PRIVATE);
                sp.edit().putString("token", response.getString("token")).apply();
                sp.edit().putLong("user_id", response.getLong("id")).apply();
                Intent in = new Intent(view.getContext(), MessageActivity.class);
                startActivityForResult(in, 0);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                NetworkResponse wr = error.networkResponse;
                if (wr != null) {

                }
            }
        });
        rq.add(jor);
    }
}
