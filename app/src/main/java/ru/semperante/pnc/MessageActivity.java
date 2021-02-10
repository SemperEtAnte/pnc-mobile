package ru.semperante.pnc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.xwray.groupie.GroupAdapter;
import com.xwray.groupie.GroupieViewHolder;
import com.xwray.groupie.databinding.BindableItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ru.semperante.pnc.databinding.ItemMessageReceiveBinding;
import ru.semperante.pnc.databinding.ItemMessageSendBinding;
import ru.semperante.pnc.models.Message;
import tech.gusavila92.websocketclient.WebSocketClient;

public class MessageActivity extends Activity {

    private WebSocketClient webSocketClient;
    private GroupAdapter<GroupieViewHolder> adapter = new GroupAdapter<>();
    private RequestQueue rq;
    private long user_id;
    private String token;
    RecyclerView rv;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.messages);
        rv = findViewById(R.id.recyclerView);
        rv.setAdapter(adapter);
        ((LinearLayoutManager)(rv.getLayoutManager())).setStackFromEnd(true);
        createWS();
        rq = Volley.newRequestQueue(this);
        user_id = getSharedPreferences("AUTHORIZATION", MODE_PRIVATE).getLong("user_id", -1);
        token = getSharedPreferences("AUTHORIZATION", MODE_PRIVATE).getString("token", "");
        System.out.println("USER_ID IS: " + user_id);
    }

    protected void createWS() {
        URI uri = null;
        try {
            uri = new URI("wss://pnc-chat.herokuapp.com/cable");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }
        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen() {
                JSONObject jo = new JSONObject();
                try {
                    jo.put("command", "subscribe");
                    JSONObject ident = new JSONObject();
                    ident.put("channel", "ChatChannel");
                    ident.put("token", token);
                    jo.put("identifier", ident.toString());
                    send(jo.toString());

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                JSONObject data = new JSONObject();
                JsonObjectRequest jor = new JsonObjectRequest(Request.Method.GET, MainActivity.HEROKU_URL + "/v1/message/list", data, response -> {
                    try {
                        JSONArray arr = response.getJSONArray("messages");
                        System.out.println("RECEIVED ARRAY: " + arr);
                        for (int i = 0; i < arr.length(); ++i) {
                            JSONObject message = arr.getJSONObject(i);
                            JSONObject user = message.getJSONObject("user");
                            long id = user.getLong("id");
                            Message m = new Message();
                            m.message = message.getString("message");
                            m.time = message.getString("created_at");
                            m.sender = user.getString("login");
                            adapter.add(0, id == user_id ? new SendMsg(m) : new ReceiveMsg(m));
                        }
                        runOnUiThread(() -> rv.scrollToPosition(arr.length() - 1));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        NetworkResponse wr = error.networkResponse;
                        if (wr != null) {
                            if (wr.statusCode == 401) {
                                getSharedPreferences("AUTHORIZATION", MODE_PRIVATE).edit().clear().apply();
                                webSocketClient.close();
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                runOnUiThread(() -> startActivityForResult(intent, 0));
                            }
                        }
                    }
                }) {

                    /**
                     * Passing some request headers
                     */
                    @Override
                    public Map<String, String> getHeaders() throws AuthFailureError {
                        HashMap<String, String> headers = new HashMap<String, String>();
                        headers.put("Content-Type", "application/json");
                        headers.put("Authorization", token);
                        return headers;
                    }
                };

                rq.add(jor);
            }

            @Override
            public void onTextReceived(String message) {
                try {
                    JSONObject jo = new JSONObject(message);
                    if (jo.has("identifier") && jo.has("message")) {

                        JSONObject msg = jo.getJSONObject("message").getJSONObject("message");
                        JSONObject user = msg.getJSONObject("user");
                        long id = user.getLong("id");
                        Message m = new Message();
                        m.message = msg.getString("message");
                        m.time = msg.getString("created_at");
                        m.sender = user.getString("login");
                        runOnUiThread(() -> adapter.add(id == user_id ? new SendMsg(m) : new ReceiveMsg(m)));
                        runOnUiThread(() -> rv.scrollToPosition(adapter.getGroupCount() - 1));
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onBinaryReceived(byte[] data) {
                System.out.println("DATA!!!!: " + Arrays.toString(data));
            }

            @Override
            public void onPingReceived(byte[] data) {
                System.out.println("Ping!!!!!!!" + Arrays.toString(data));
            }

            @Override
            public void onPongReceived(byte[] data) {
                System.out.println("PONG READED!!!! " + Arrays.toString(data));
            }

            @Override
            public void onException(Exception e) {
                System.out.println("ERRROR" + e.getMessage());
                e.printStackTrace();
            }

            @Override
            public void onCloseReceived() {
                System.out.println("CLOSED!!!!!");
            }

        };
        webSocketClient.connect();
    }

    public void sendMessage(View view) {
        System.out.println("I CALLED!!!!");
        EditText et = findViewById(R.id.sending_text);
        String text = et.getText().toString();
        System.out.println("TEXT: " + text);
        if (text.length() > 0) {
            JSONObject data = new JSONObject();
            try {
                data.put("message", text);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            JsonObjectRequest jor = new JsonObjectRequest(Request.Method.POST, MainActivity.HEROKU_URL + "/v1/message/send", data, response -> {
            }, error -> {
                NetworkResponse wr = error.networkResponse;
                if (wr != null) {
                    if (wr.statusCode == 401) {
                        getSharedPreferences("AUTHORIZATION", MODE_PRIVATE).edit().clear().apply();
                        webSocketClient.close();
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        startActivityForResult(intent, 0);
                    }
                }
            }) {

                /**
                 * Passing some request headers
                 */
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> headers = new HashMap<String, String>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Authorization", token);
                    return headers;
                }
            };
            rq.add(jor);
            et.setText("");
        }
    }

    public void logout(View view) {
        JSONObject data = new JSONObject();
        JsonObjectRequest jor = new JsonObjectRequest(Request.Method.POST, MainActivity.HEROKU_URL + "/v1/user/logout", data, response -> {
            getSharedPreferences("AUTHORIZATION", MODE_PRIVATE).edit().clear().apply();
            webSocketClient.close();
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivityForResult(intent, 0);
        }, error -> {
            getSharedPreferences("AUTHORIZATION", MODE_PRIVATE).edit().clear().apply();
            webSocketClient.close();
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivityForResult(intent, 0);

        }) {
            /**
             * Passing some request headers
             */
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", token);
                return headers;
            }
        };
        rq.add(jor);
    }

    public class ReceiveMsg extends BindableItem<ItemMessageSendBinding> {
        private final Message message;

        public ReceiveMsg(Message message) {
            this.message = message;
        }

        @Override
        public void bind(@NonNull ItemMessageSendBinding viewBinding, int position) {
            viewBinding.setMessage(message);
        }

        @Override
        public int getLayout() {
            return R.layout.item_message_send;
        }
    }

    public class SendMsg extends BindableItem<ItemMessageReceiveBinding> {
        private final Message message;

        public SendMsg(Message message) {
            this.message = message;
        }

        @Override
        public void bind(@NonNull ItemMessageReceiveBinding viewBinding, int position) {
            viewBinding.setMessage(message);
        }

        @Override
        public int getLayout() {
            return R.layout.item_message_receive;
        }
    }
}
