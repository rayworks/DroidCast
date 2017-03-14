package com.rayworks.droidcast;

import android.graphics.Bitmap;
import android.os.Looper;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import java.io.ByteArrayOutputStream;

/**
 * Created by seanzhou on 3/14/17.
 */

public class Main {
    static Looper looper;

    public static void main(String[] args) {
        AsyncHttpServer httpServer = new AsyncHttpServer() {
            @Override
            protected boolean onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                return super.onRequest(request, response);
            }
        };

        Looper.prepare();

        looper = Looper.myLooper();
        System.out.println(">>> DroidCast main entry");

        AsyncServer server = new AsyncServer();
        httpServer.get("/screenshot.jpg", new AnyRequestCallback());
        httpServer.listen(server, 53516);

        looper.loop();
    }

    static class AnyRequestCallback implements HttpServerRequestCallback {
        @Override
        public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
            try {
                Bitmap bitmap = ScreenCaptor.screenshot(360, 640);

                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bout);
                bout.flush();
                response.send("image/jpeg", bout.toByteArray());

            } catch (Exception e) {
                response.code(500);
                response.send(e.toString());
            }
        }
    }
}
