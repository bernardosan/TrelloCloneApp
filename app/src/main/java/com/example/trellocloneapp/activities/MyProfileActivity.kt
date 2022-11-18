package com.example.trellocloneapp.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.trellocloneapp.R
import com.example.trellocloneapp.databinding.ActivityMyProfileBinding
import com.example.trellocloneapp.firebase.FirestoreClass
import com.example.trellocloneapp.models.User
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.launch
import java.io.IOException

class MyProfileActivity : BaseActivity() {

    private var binding: ActivityMyProfileBinding? = null
    private var mSelectedImageFileUri: Uri? = null
    private var mProfileImageUri: String = ""

    private val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()){
            result ->
        if(result.resultCode == RESULT_OK && result.data!=null){
            val image: ImageView = findViewById(R.id.iv_my_profile)
            mSelectedImageFileUri = result.data?.data
            image.setImageURI(mSelectedImageFileUri)
        }
    }

    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries
        }

    private val localStorageLauncher : ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            isGranted -> if(isGranted){
        val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            openGalleryLauncher.launch(pickIntent)
        } catch (e: IOException){
            e.printStackTrace()
        }

    } else {
        Toast.makeText(this, "Denied Permission of storage", Toast.LENGTH_SHORT).show()
    }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyProfileBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        setupActionBar()

        FirestoreClass().signInUser(this)

        binding?.toolbarMyProfile?.setNavigationOnClickListener {
            onBackPressed()
        }

        binding?.btnUpdate?.setOnClickListener {

            if(mSelectedImageFileUri != null){
                uploadUserImage()
            }

        }

        binding?.ivMyProfile?.setOnClickListener {
            updateImageView()
        }

    }

    private fun updateImageView() {
        requestStoragePermission()

        if(isReadStorageAllowed()){
            showProgressDialog(getString(R.string.please_wait))
            lifecycleScope.launch {
                localStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                hideProgressDialog()
            }

        }
    }



    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        return result
    }

    private fun requestStoragePermission(){
        // Check if the permission was denied and show rationale
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
            showRationaleDialog(getString(R.string.app_name),getString(R.string.app_name) +
                    "needs to Access Your External Storage")
        }
        else {
            requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }

    }

    private fun showRationaleDialog(
        title: String,
        message: String,
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message).setPositiveButton("Cancel") {
                dialog, _ -> dialog.dismiss()
        }
        builder.create().show()
    }

    private fun updateUser() {
        TODO("Not yet implemented")
    }


    private fun uploadUserImage(){
        showProgressDialog(resources.getString(R.string.please_wait))

        if(mSelectedImageFileUri != null){

            val sRef : StorageReference = FirebaseStorage.getInstance().reference.child(
                "USER_IMAGE" + System.currentTimeMillis() +
                        "." + getFileExtension(mSelectedImageFileUri))

            sRef.putFile(mSelectedImageFileUri!!).addOnSuccessListener {
                taskSnapshot -> 
                Log.i("Firebase Image URL", taskSnapshot.metadata!!.reference!!.downloadUrl.toString())
                taskSnapshot.metadata!!.reference!!.downloadUrl.addOnSuccessListener {
                    uri ->
                    Log.i("Downloadable Image URL", uri.toString())
                    mProfileImageUri = uri.toString()
                }
            }.addOnFailureListener {
                exception ->
                Log.e("Firebase Image", "${exception.message}")
                exception.printStackTrace()
                Toast.makeText(this, "${exception.message}", Toast.LENGTH_SHORT).show()
            }

            hideProgressDialog()
            
        }
    }


    // get files extensions from Uri
    private fun getFileExtension(uri: Uri?): String?{
        return MimeTypeMap
            .getSingleton()
            .getExtensionFromMimeType(contentResolver.getType(uri!!))
    }

    private fun setupActionBar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_my_profile)
        setSupportActionBar(findViewById(R.id.toolbar_my_profile))
        toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        toolbar.title = resources.getString(R.string.my_profile)
    }

    fun setUserDataInUI(user: User) {
        Glide
            .with(this)
            .load(user.image)
            .centerCrop()
            .placeholder(R.drawable.ic_user_place_holder)
            .into(findViewById(R.id.iv_my_profile))

        binding?.etNameMyprofile?.setText(user.name)
        binding?.etEmailMyprofile?.setText(user.email)
        if(user.mobile != 0L){
            binding?.etMobileMyprofile?.setText(user.mobile.toString())
        }
    }


}