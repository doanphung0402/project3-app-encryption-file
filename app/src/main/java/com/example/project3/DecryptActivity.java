package com.example.project3;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.loader.content.CursorLoader;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;


import com.example.FIleEncryptUtils.AES_BC;
import com.example.FIleEncryptUtils.KeyStoreUtils;
import com.example.FIleEncryptUtils.MAC_BC;
import com.example.FIleEncryptUtils.Utils_BC;
import com.example.project3.UtilsEncypt.DecryptFile;
import com.example.project3.UtilsEncypt.EncryptFile;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DecryptActivity extends AppCompatActivity {
    ArrayList<FileChooserInfo> listFile = new ArrayList<>();
    ArrayList<Uri> fileChooser = new ArrayList<>();
    List<String> listPositionFileDecrypt  = new ArrayList<>();
    ListView listView;
    FileChooserADDecrypt fileChooserAdapter;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkAndRequestPermissions();
        setContentView(R.layout.activity_decrypt);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("file-position"));
        listView = (ListView) findViewById(R.id.list_view_file_decrypt);
        Button btn_continue = (Button) findViewById(R.id.continue_btn_decrypt);
        String path = Environment.getExternalStorageDirectory().getPath()
                +  File.separator + "Android" + File.separator+"data/com.example.project3/file_encript";
        File file = new File(path);
        if(file.exists()){
             File[] files = file.listFiles();
             for(int k =0 ;k < files.length; k++){
                  fileChooser.add(Uri.parse(files[k].getPath()));
             }
             Log.i("file chooser length", String.valueOf(fileChooser.size()));
            for (Uri urlFile : fileChooser) {
                Log.i("uri file", String.valueOf(urlFile));
                String nameFile =String.valueOf(urlFile).substring(path.lastIndexOf("/")+1);
                Uri pathFile = urlFile;
                String extentionFile = getExtensionByStringHandling(nameFile).get();
                int iconFile = getImageIconFile(extentionFile);
                listFile.add(new FileChooserInfo(nameFile, pathFile, iconFile));
            }
            Button btnDecryptFile = (Button) findViewById(R.id.continue_btn_decrypt);
            if (listFile.isEmpty()){
                 btnDecryptFile.setEnabled(false);
            }
            fileChooser.clear();
            fileChooserAdapter = new FileChooserADDecrypt(this, R.layout.file_decrypt_item, listFile);
            listView.setAdapter(fileChooserAdapter);
        }
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(DecryptActivity.this);
                dialog.setTitle("Xác nhận ");
                dialog.setMessage("Bạn có đồng ý xóa không ? ");
                dialog.setPositiveButton("Đồng ý ", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        listFile.remove(position);
                        fileChooserAdapter.notifyDataSetChanged();
                    }
                });
                dialog.setNegativeButton("Không", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });
                AlertDialog alertDialog = dialog.create();
                alertDialog.show();
                return false;
            }
        });
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        View empty = findViewById(R.id.empty_decrypt_list);
        ListView list = (ListView) findViewById(R.id.list_view_file_decrypt);
        list.setEmptyView(empty);



    }

    int requestcode = 1;
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static Optional<String> getExtensionByStringHandling(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)

    public void onActivityResult(int requestcode, int resultCode, Intent data) {
        ListView lv = (ListView) findViewById(R.id.list_view_file_decrypt);

        super.onActivityResult(requestcode, resultCode, data);

    }



    private void checkAndRequestPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(permission);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), 1);
        }
    }

    @SuppressLint("Range")
    public  String getFileName(Uri uri, Context context) {
        String res = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    res = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

                }
            } finally {
                cursor.close();
            }
            if (res == null) {
                res = uri.getPath();
                int cutt = res.lastIndexOf('/');
                if (cutt != -1) {
                    res = res.substring(cutt + 1);

                }
            }
        }

        return res;
    }

    @SuppressLint("Range")


    public int getImageIconFile(String extentionFile) {
        int nameImg;
        switch (extentionFile) {
            case "pdf":
                nameImg = R.drawable.pdf_image;
                break;
            case "docx":
                nameImg = R.drawable.word_image;
                break;
            case "mp4":
                nameImg = R.drawable.mp4_image;
                break;
            case "jpg":
                nameImg = R.drawable.jpg_image;
                break;
            case "mp3":
                nameImg = R.drawable.mp3_icon;
                break;
            case "txt":
                nameImg = R.drawable.text_image;
                break;
            case "rar":
                nameImg = R.drawable.rar_icon;
                break;
            case "pptx":
                nameImg = R.drawable.pptx_image;
                break;
            default:
                nameImg = R.drawable.unknown_image;
        }

        return nameImg;

    }

    public BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
                       String qty = intent.getStringExtra("position");
            listPositionFileDecrypt.add(qty);

        }
    };
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void decriptFile(View view)  {
        String EXTERNAL_PATH = Environment.getExternalStorageDirectory().getPath();
        Log.i("size list", String.valueOf(listFile.size()));
        ArrayList<FileChooserInfo> listFileDecrypt = new ArrayList<>();
        if(listPositionFileDecrypt.isEmpty()){
             listFileDecrypt = listFile ;
        }else{
            for (String position :listPositionFileDecrypt){
                Log.i("postion",position);
                listFileDecrypt.add(listFile.get(Integer.parseInt(position)));
            }
        }
        Log.i("list file Decrypt", String.valueOf(listFileDecrypt.size()));

        DecryptFile decrypt = new DecryptFile(listFileDecrypt,getApplicationContext());
        decrypt.decryptFile();
        listFileDecrypt.clear();
        finish();
        startActivity(getIntent());
    }
}
