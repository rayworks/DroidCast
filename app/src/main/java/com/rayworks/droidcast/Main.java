package com.rayworks.droidcast;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Looper;
import android.os.Process;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** Created by seanzhou on 3/14/17. */
public class Main {
    static Looper looper;
    static int width = 0;
    static int height = 0;

    public static void main(String[] args) {
        AsyncHttpServer httpServer =
                new AsyncHttpServer() {
                    @Override
                    protected boolean onRequest(
                            AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                        return super.onRequest(request, response);
                    }
                };

        Looper.prepare();

        looper = Looper.myLooper();
        System.out.println(">>> DroidCast main entry");

        AsyncServer server = new AsyncServer();
        httpServer.get("/screenshot.jpg", new AnyRequestCallback());
        httpServer.listen(server, 53516);

        Looper.loop();
    }

    static class AnyRequestCallback implements HttpServerRequestCallback {
        @Override
        public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
            try {
                if (width == 0 || height == 0) {
                    // dimension initialization
                    Point point = DisplayUtil.getCurrentDisplaySize();

                    if (point != null && point.x > 0 && point.y > 0) {
                        width = point.x;
                        height = point.y;
                    } else {
                        width = 360;
                        height = 640;
                    }
                }
                Bitmap bitmap = ScreenCaptor.screenshot(width, height);

                if (bitmap == null) {
                    System.out.println(
                            String.format(
                                    Locale.ENGLISH,
                                    ">>> failed to generate image with resolution %d:%d",
                                    width,
                                    height));

                    width /= 2;
                    height /= 2;

                    bitmap = ScreenCaptor.screenshot(width, height);
                }

                System.out.println(
                        String.format(
                                Locale.ENGLISH,
                                "Bitmap generated with resolution %d:%d, process id %d | thread id %d",
                                width,
                                height,
                                Process.myPid(),
                                Process.myTid()));

                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bout);
                bout.flush();
                response.send("image/jpeg", bout.toByteArray());

                // "Make sure to call Bitmap.recycle() as soon as possible, once its content is not
                // needed anymore."
                bitmap.recycle();

            } catch (Exception e) {
                response.code(500);
                response.send(e.toString());
            }
        }
    }
}
