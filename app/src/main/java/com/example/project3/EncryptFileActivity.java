package com.example.project3;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.loader.content.CursorLoader;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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

public class EncryptFileActivity extends AppCompatActivity {
    ArrayList<FileChooserInfo> listFile = new ArrayList<>();
    ArrayList<Uri> fileChooser = new ArrayList<>();

    ListView listView;
    FileChooserAdapter fileChooserAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        checkAndRequestPermissions();
        setContentView(R.layout.activity_encrypt);
        listView = (ListView) findViewById(R.id.list_view_file);
        Button btn_continue = (Button) findViewById(R.id.continue_btn_encrypt);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                AlertDialog.Builder dialog = new AlertDialog.Builder(EncryptFileActivity.this);
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
        View empty = findViewById(R.id.empty);
        ListView list = (ListView) findViewById(R.id.list_view_file);
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
        ListView lv = (ListView) findViewById(R.id.list_view_file);

        super.onActivityResult(requestcode, resultCode, data);
        if (requestcode == requestcode && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }
            if (null != data.getClipData()) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    fileChooser.add(uri);
                }
            } else {
                Uri uri = data.getData();
                fileChooser.add(uri);
            }
            for (Uri urlFile : fileChooser) {
                String nameFile = getFileName(urlFile, getApplicationContext());
                Uri pathFile = urlFile;
                String extentionFile = getExtensionByStringHandling(nameFile).get();
                int iconFile = getImageIconFile(extentionFile);
                listFile.add(new FileChooserInfo(nameFile, pathFile, iconFile));
            }
            fileChooser.clear();
            fileChooserAdapter = new FileChooserAdapter(this, R.layout.file_chooser_info_item_activity, listFile);
            lv.setAdapter(fileChooserAdapter);
        } //end if onActiivity
    }

    public void openFileChooser(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(("*/*"));
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, requestcode);
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
    public String getFileName(Uri uri, Context context) {
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

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void encriptFile(View view) {
      String EXTERNAL_PATH = Environment.getExternalStorageDirectory().getPath();

           EncryptFile encrypt = new EncryptFile(listFile,getApplicationContext());
           encrypt.encriptFile();
           listFile.clear();
           finish();
           startActivity(getIntent());
           Log.i("encrypt","EncryptFileActivity done!");
//        File file = new File(EXTERNAL_PATH+"/DCIM/Camera/20220526_160441.jpg");
//        if(file.exists()){
//             boolean st = file.delete();
//             Log.i("status delete", String.valueOf(st));
//
//        }else{
//             Log.i("delete","error");
//        }
//        try {
//                   AES_BC aes_bc = new AES_BC() ;
//                   String EXTERNAL_PATH = Environment.getExternalStorageDirectory().getPath();
//                   String INTERNAL_PATH = Environment.getDataDirectory().getPath();
//
//                   aes_bc.createKey("key");
//                   SecretKey secretKey = aes_bc.getKey("key");
//                   if(secretKey==null){
//                       Log.i("null","null");
//                   }
//                   MAC_BC mac_bc = new MAC_BC();
//                   String pathfile = EXTERNAL_PATH +"/Android/data/com.example.project3" ;
//                   File fileInput = new File(pathfile+"/folder.intermediate/file.jpg");
//                   File outPut = new File(pathfile+"/folder.intermediate/file.encrypt");
//                   File outDecryptFile = new File(pathfile +"/folder.intermediate/file1.jpg");
//                   File macFile = new File(pathfile +"/folder.intermediate/macFile.mac");
//                   outPut.createNewFile();
//                   outDecryptFile.createNewFile();
//                   macFile.createNewFile();
//                   File fileTotal = new File(pathfile+"/file_encript/file.total");
//                   File fileOriginDec =  new File(pathfile+"/file_encript/fileOrigin.jpg");
//                   File fileOriginEnc =  new File(pathfile+"/file_encript/fileOrigin.encr");
//                   fileTotal.createNewFile();
//                   fileOriginDec.createNewFile();
//                   fileOriginEnc.createNewFile();
//                   byte[] iv = aes_bc.encriptFile(secretKey,fileInput,outPut);
//                   aes_bc.decryptFile(secretKey,iv,outPut,outDecryptFile);
//                   mac_bc.encryptFileWithHmac(secretKey,outPut,macFile);
//                   Utils_BC utils_bc = new Utils_BC(1024);
//                   utils_bc.createFileProtected(outPut,macFile,iv,fileTotal);
//                   Log.i("done","Done1");
//                   boolean flag=  utils_bc.encrypfileFromFileProtected(fileTotal,fileOriginEnc,fileOriginDec);
//                   Log.i("flag", String.valueOf(flag));
//                   Log.i("done","Done");
////            fileOriginEnc.delete();
////            fileTotal.delete();
//
//               }catch(IOException e){
//                   Log.i("error ioe",e.getMessage());
//               }catch(NoSuchAlgorithmException e){
//                   Log.i("error",e.getMessage());
//               }
//               catch(InvalidKeyException e){
//                   Log.i("error",e.getMessage());
//               }
//               catch(NoSuchProviderException e){
//                   Log.i("error",e.getMessage());
//               } catch (GeneralSecurityException e) {
//                   e.printStackTrace();
//               }
        }
    }

