package com.example.activevisiondemo;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private Button selectFileButton; // 按钮声明
    private ActivityResultLauncher<Intent> filePickerLauncher; // 新的文件选择器启动器

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 绑定按钮
        selectFileButton = findViewById(R.id.select_file_button);

        // 注册文件选择器的ActivityResultLauncher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri selectedFileUri = result.getData().getData();
                        Log.d(TAG, "File Uri: " + selectedFileUri.toString());
                        if (selectedFileUri != null) {
                            try {
                                uploadFile(selectedFileUri);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });

        checkStoragePermission(); // 检查存储权限

        // 设置按钮点击事件
        selectFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectFile();
            }
        });
    }

    // 权限请求结果的回调方法
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                testConnection();
                Log.d(TAG, "看看网络通没通");
                // 如果用户同意了权限请求，启动文件选择器
                selectFile();
                Log.d(TAG, "进入选择文件过程");
            } else {
                Log.e(TAG, "读取存储权限被拒绝，无法选择文件。");
            }
        }
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }


    // 打开文件选择器的方法
    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // 允许用户选择任何类型的文件
        filePickerLauncher.launch(intent); // 使用新的Activity Result API启动文件选择器
    }

    private void testConnection() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("http://150.203.2.193:8080")  // 目标服务器URL
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "无法连接到服务器: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "连接成功: " + response.body().string());
                } else {
                    Log.e(TAG, "服务器响应失败: " + response.message());
                }
            }
        });
    }

    private void uploadFile(Uri fileUri) {
        // 获取文件的 MIME 类型
        String mimeType = getContentResolver().getType(fileUri);

        // 使用 ContentResolver 获取文件输入流
        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);

            if (inputStream == null) {
                Log.e(TAG, "无法打开文件输入流");
                return;
            }

            // 将输入流转化为字节数组
            byte[] fileBytes = new byte[inputStream.available()];
            inputStream.read(fileBytes);

            // 创建请求体
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "uploaded_file.mp4", // 你可以根据需要修改文件名
                            RequestBody.create(MediaType.parse(mimeType), fileBytes))
                    .build();

            // 构建请求
            Request request = new Request.Builder()
                    .url("http://172.20.10.3:5000/upload")  // 替换为服务端地址
                    .post(requestBody)
                    .build();

            // 发起请求
            OkHttpClient client = new OkHttpClient();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "上传失败: " + e.getMessage());
                }

//                @Override
//                public void onResponse(Call call, Response response) throws IOException {
//                    if (response.isSuccessful()) {
//                        String responseData = response.body().string();
//                        Log.d(TAG, "服务端返回: " + responseData);
//                    } else {
//                        Log.e(TAG, "服务器错误: " + response.message());
//                    }
//                }

//                @Override
//                public void onResponse(Call call, Response response) throws IOException {
//                    if (response.isSuccessful()) {
//                        // 将MP4文件保存到本地存储
//                        InputStream inputStream = response.body().byteStream();
//                        File videoFile = new File(getExternalFilesDir(null), "downloaded_video.mp4");
//
//                        FileOutputStream fileOutputStream = new FileOutputStream(videoFile);
//                        byte[] buffer = new byte[2048];
//                        int bytesRead;
//                        while ((bytesRead = inputStream.read(buffer)) != -1) {
//                            fileOutputStream.write(buffer, 0, bytesRead);
//                        }
//
//                        fileOutputStream.close();
//                        inputStream.close();
//
//                        // 文件保存成功，打印保存的路径
//                        Log.d(TAG, "保存的视频文件路径: " + videoFile.getAbsolutePath());
//
//                        // 文件保存成功，提示用户
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                new AlertDialog.Builder(MainActivity.this)
//                                        .setTitle("视频下载完成")
//                                        .setMessage("点击确认播放视频")
//                                        .setPositiveButton("播放", new DialogInterface.OnClickListener() {
//                                            @Override
//                                            public void onClick(DialogInterface dialog, int which) {
//                                                // 播放下载的视频
//                                                playVideo(videoFile);
//                                            }
//                                        })
//                                        .setNegativeButton("取消", null)
//                                        .show();
//                            }
//                        });
//                    } else {
//                        Log.e(TAG, "服务器错误: " + response.message());
//                    }
//                }


//                @Override
//                public void onResponse(Call call, Response response) throws IOException {
//                    if (response.isSuccessful()) {
//                        // 从响应中获取文件输入流
//                        InputStream inputStream = response.body().byteStream();
//
//                        // 将文件保存到公共存储的Downloads目录
//                        File downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
//                        File videoFile = new File(downloadsDirectory, "downloaded_video.mp4");
//
//                        FileOutputStream fileOutputStream = new FileOutputStream(videoFile);
//                        byte[] buffer = new byte[2048];
//                        int bytesRead;
//                        while ((bytesRead = inputStream.read(buffer)) != -1) {
//                            fileOutputStream.write(buffer, 0, bytesRead);
//                        }
//
//                        fileOutputStream.close();
//                        inputStream.close();
//
//                        Log.d(TAG, "视频已保存到Downloads目录: " + videoFile.getAbsolutePath());
//
//                        // 提示用户视频已下载
//                        runOnUiThread(() -> {
//                            new AlertDialog.Builder(MainActivity.this)
//                                    .setTitle("视频下载完成")
//                                    .setMessage("点击确认播放视频")
//                                    .setPositiveButton("播放", (dialog, which) -> playVideo(videoFile))
//                                    .setNegativeButton("取消", null)
//                                    .show();
//                        });
//                    } else {
//                        Log.e(TAG, "服务器错误: " + response.message());
//                    }
//                }


                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        // 从响应中获取文件输入流
                        InputStream inputStream = response.body().byteStream();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Android 10 及以上版本，使用 MediaStore 保存文件到 Downloads
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "downloaded_video.mp4");
                            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                            Uri externalUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                            Uri fileUri = getContentResolver().insert(externalUri, values);

                            try {
                                if (fileUri != null) {
                                    OutputStream outputStream = getContentResolver().openOutputStream(fileUri);
                                    if (outputStream != null) {
                                        byte[] buffer = new byte[2048];
                                        int bytesRead;
                                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                                            outputStream.write(buffer, 0, bytesRead);
                                        }
                                        outputStream.close();
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            // Android 10 以下版本，使用 Environment.getExternalStoragePublicDirectory 保存文件
                            File downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            File videoFile = new File(downloadsDirectory, "downloaded_video.mp4");

                            FileOutputStream fileOutputStream = new FileOutputStream(videoFile);
                            byte[] buffer = new byte[2048];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                fileOutputStream.write(buffer, 0, bytesRead);
                            }

                            fileOutputStream.close();
                            inputStream.close();

                            Log.d(TAG, "视频已保存到Downloads目录: " + videoFile.getAbsolutePath());

                            // 提示用户视频已下载
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("视频下载完成")
                                        .setMessage("点击确认播放视频")
                                        .setPositiveButton("播放", (dialog, which) -> playVideo(videoFile))
                                        .setNegativeButton("取消", null)
                                        .show();
                            });
                        }

                        // 提示用户视频已下载
                        runOnUiThread(() -> {
                            // 使用 Toast 提示
                            Toast.makeText(MainActivity.this, "视频下载完成", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        Log.e(TAG, "服务器错误: " + response.message());
                    }
                }


            });

        } catch (Exception e) {
            Log.e(TAG, "文件上传失败", e);
        }
    }

//    private void playVideo(File videoFile) {
//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        Uri videoUri = Uri.fromFile(videoFile);
//        intent.setDataAndType(videoUri, "video/mp4");
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(intent);
//    }

//    private void playVideo(File videoFile) {
//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        Uri videoUri = Uri.fromFile(videoFile);
//        intent.setDataAndType(videoUri, "video/mp4");
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(intent);
//    }

    private void playVideo(File videoFile) {
        Intent intent = new Intent(Intent.ACTION_VIEW);

        Uri videoUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 及以上版本，直接使用 Uri
            ContentResolver contentResolver = getContentResolver();
            String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[] { "downloaded_video.mp4" };

            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

            Cursor cursor = contentResolver.query(collection, null, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
                long id = cursor.getLong(idColumn);

                videoUri = ContentUris.withAppendedId(collection, id);
            } else {
                Log.e(TAG, "未找到下载的视频文件");
                return;
            }
            cursor.close();
        } else {
            // Android 10 以下版本，使用文件的 Uri
            videoUri = Uri.fromFile(videoFile);
        }

        intent.setDataAndType(videoUri, "video/mp4");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // 授予读取 URI 权限
        startActivity(intent);
    }



}
