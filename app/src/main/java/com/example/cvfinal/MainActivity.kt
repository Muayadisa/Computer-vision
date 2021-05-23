package com.example.cvfinal
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.ktx.Firebase
import com.google.gson.*
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var photo: ImageView
    private lateinit var functions: FirebaseFunctions
    private val SELECT_PICTURE: Int = 200
    private var photoURI: Uri? = null
    private lateinit var locationName: TextView
    private lateinit var detectButton: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        photo = findViewById(R.id.imageView)
        locationName = findViewById(R.id.textView)
        detectButton = findViewById(R.id.detect)
        auth = Firebase.auth

        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("Signin", "Detection ready")
                    val user = auth.currentUser
                    detectButton.isEnabled = true
                } else {
                    Log.w("Signin", "signInAnonymously:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

    }
    fun selectImage(view: View) {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Pmage"), SELECT_PICTURE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                if (data != null) {
                    photoURI = data.data!!
                    photo.setImageURI(photoURI)
                }
            }
        }
    }
    fun detectImage(view: View) {
        if (photoURI == null)
            return
        var bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, photoURI)
        bitmap = scaleBitmapDown(bitmap, 640)
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes: ByteArray = byteArrayOutputStream.toByteArray()
        val base64encoded = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        functions = FirebaseFunctions.getInstance()

        val request = JsonObject()
        val image = JsonObject()
        image.add("content", JsonPrimitive(base64encoded))
        request.add("image", image)
        val feature = JsonObject()
        feature.add("maxResults", JsonPrimitive(5))
        feature.add("type", JsonPrimitive("LANDMARK_DETECTION"))
        val features = JsonArray()
        features.add(feature)
        request.add("features", features)

        annotateImage(request.toString())
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(this, "Failed", Toast.LENGTH_LONG).show()
                    val e = task.exception
                    if (e is FirebaseFunctionsException) {
                        val code = e.code
                        val details = e.details
                        Log.d("FirebaseFail", "detectImage: $code, $details")
                    }
                }
                else {
                    for (label in task.result!!.asJsonArray[0].asJsonObject["landmarkAnnotations"].asJsonArray) {
                        val labelObj = label.asJsonObject
                        val landmarkName = labelObj["description"]
                        locationName.text = landmarkName.asString
                        val entityId = labelObj["mid"]
                        val score = labelObj["score"]
                        val bounds = labelObj["boundingPoly"]
                        for (loc in labelObj["locations"].asJsonArray) {
                            val latitude = loc.asJsonObject["latLng"].asJsonObject["latitude"]
                            val longitude = loc.asJsonObject["latLng"].asJsonObject["longitude"]
                        }
                    }

                }
            }
    }
    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = maxDimension
        var resizedHeight = maxDimension
        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension
            resizedWidth =
                (resizedHeight * originalWidth.toFloat() / originalHeight.toFloat()).toInt()
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight =
                (resizedWidth * originalHeight.toFloat() / originalWidth.toFloat()).toInt()
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }
    private fun annotateImage(requestJson: String): Task<JsonElement> {
        return functions
            .getHttpsCallable("annotateImage")
            .call(requestJson)
            .continueWith { task ->
                val result = task.result?.data
                JsonParser.parseString(Gson().toJson(result))
            }
    }
}