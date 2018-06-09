package br.com.bossini.meualbumdefotosfatecipinoite;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private StorageReference imagesStorageReference;
    private DatabaseReference fileNameGenerator;
    private DatabaseReference urlsReference;

    private RecyclerView fotosRecyclerView;
    private List <Bitmap> fotos;
    private static final int REQUEST_PERMISSION_CAMERA = 2001;
    private static final int REQUEST_TAKE_PICTURE = 1001;
    private static final String PNG_EXTENSION = ".png";
    private static final long UM_MEGA = 1000 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab =
                (FloatingActionButton) findViewById(R.id.fab);
        configurarMinhaRecyclerView();
        fab.setOnClickListener(fabListener);
    }

    @Override
    protected void onStart() {
        super.onStart();
        configurarMeuFirebase();
    }

    private void updateRecyclerViewList(Bitmap foto){
        fotos.add(foto);
        fotosRecyclerView.getAdapter().notifyDataSetChanged();
    }

    private void saveUrlForDownload (String chave, Uri downloadUrl){
        this.urlsReference.child(chave).setValue(downloadUrl.toString());
    }
    private void uploadImage (final Bitmap foto){
        final String chave = this.fileNameGenerator.push().getKey();
        StorageReference storageReference =
                imagesStorageReference.child(chave + PNG_EXTENSION);
        byte [] data =
                Utils.toByteArray(foto);
        UploadTask task = storageReference.putBytes(data);
        task.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                updateRecyclerViewList(foto);
                Uri downloadUrl =
                        taskSnapshot.getDownloadUrl();
                saveUrlForDownload (downloadUrl, chave);
                Toast.makeText(MainActivity.this,
                        getString(R.string.sucesso_no_upload),
                        Toast.LENGTH_SHORT).show();
            }
        });
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.falha_no_upload),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configurarMeuFirebase (){
        //configura storage
        final FirebaseStorage firebaseStorage =
                FirebaseStorage.getInstance();
        StorageReference rootStorageReference =
                firebaseStorage.getReference();
        this.imagesStorageReference =
                rootStorageReference.child("images");

        FirebaseDatabase firebaseDatabase =
                FirebaseDatabase.getInstance();
        this.fileNameGenerator =
                firebaseDatabase.getReference("image_names");
        this.urlsReference =
                firebaseDatabase.getReference("urls");
        this.urlsReference.
                addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                fotos.clear();
                for (DataSnapshot filho : dataSnapshot.getChildren()){
                    //cada iteração representa um download
                    String url =
                            filho.getValue() + PNG_EXTENSION;
                    StorageReference aux =
                            firebaseStorage.getReferenceFromUrl(url);
                    Task <byte[]> task = aux.getBytes(UM_MEGA);
                    task.addOnSuccessListener(new OnSuccessListener<byte[]>() {
                        @Override
                        public void onSuccess(byte[] bytes) {
                            updateRecyclerViewList(Utils.toBitmap(bytes));
                        }
                    });
                    task.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.falha_no_download),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }

            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(MainActivity.this,
                        getString(R.string.falha_conexao_fb),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PICTURE){
            if (resultCode == Activity.RESULT_OK){
                Bitmap foto = (Bitmap) data.getExtras().get("data");
                uploadImage(foto);
            }
            else{
                Toast.makeText(this,
                        getString(R.string.foto_nao_confirmada),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_CAMERA){
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED){
                tirarFoto();
            }
            else{
                Toast.makeText(this,
                        getString(R.string.explicacao_permissao_camera),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void tirarFoto (){
        Intent tirarFotoIntent =
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(tirarFotoIntent, REQUEST_TAKE_PICTURE);
    }

    private View.OnClickListener fabListener =
            new View.OnClickListener(){

                @Override
                public void onClick(View v) {
                    if (ActivityCompat.
                            checkSelfPermission(MainActivity.this,
                                    Manifest.permission.CAMERA) !=
                            PackageManager.PERMISSION_GRANTED){
                        //ainda não tem a permissão
                        if (ActivityCompat.
                                shouldShowRequestPermissionRationale(MainActivity.this,
                                        Manifest.permission.CAMERA)){
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.explicacao_permissao_camera),
                                    Toast.LENGTH_SHORT).show();
                        }
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.CAMERA},
                                REQUEST_PERMISSION_CAMERA);
                    }
                    else{
                        //já tem a permissão
                        tirarFoto();
                    }
                }
            };

    private void configurarMinhaRecyclerView (){
        fotosRecyclerView =
                findViewById(R.id.fotosRecyclerView);
        fotos =
                new LinkedList<>();
        fotosRecyclerView.setAdapter(new FotosAdapter(fotos, this));
        fotosRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
    }

    private static class FotosViewHolder extends
            RecyclerView.ViewHolder{
        private View raiz;
        private ImageView fotoImageView;
        FotosViewHolder(View raiz){
            super(raiz);
            this.raiz = raiz;
            this.fotoImageView =
                    raiz.findViewById(R.id.fotoImageView);
        }
    }



    private class FotosAdapter extends RecyclerView.Adapter <FotosViewHolder>{

        private List <Bitmap> fotos;
        private Context context;

        public FotosAdapter (List <Bitmap> fotos, Context context){
            this.fotos = fotos;
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return fotos.size();
        }

        @NonNull
        @Override
        public FotosViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater =
                    LayoutInflater.from(context);
            View raiz =
                    inflater.inflate(R.layout.fotos_layout, parent, false);
            FotosViewHolder fotosViewHolder =
                    new FotosViewHolder(raiz);
            return fotosViewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull FotosViewHolder holder, int position) {
            Bitmap caraDaVez = fotos.get(position);
            holder.fotoImageView.setImageBitmap(caraDaVez);
        }






    }



}
