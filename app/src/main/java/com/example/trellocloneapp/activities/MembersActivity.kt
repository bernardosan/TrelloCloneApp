package com.example.trellocloneapp.activities

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trellocloneapp.R
import com.example.trellocloneapp.adapters.MembersListAdapter
import com.example.trellocloneapp.databinding.ActivityMembersBinding
import com.example.trellocloneapp.firebase.FirestoreClass
import com.example.trellocloneapp.models.Board
import com.example.trellocloneapp.models.User
import com.example.trellocloneapp.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class MembersActivity : BaseActivity() {

    private lateinit var mAssignedMemberList: ArrayList<User>
    private lateinit var mBoardDetails: Board
    private var binding: ActivityMembersBinding? = null
    private var anyChangesMade: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMembersBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        setupActionBar()

        if(intent.hasExtra(Constants.BOARD_DETAIL)){
            mBoardDetails = intent.getParcelableExtra(Constants.BOARD_DETAIL)!!
        }

        binding?.toolbarMembersActivity?.setNavigationOnClickListener {
            onBackPressed()
        }

        showProgressDialog(resources.getString(R.string.please_wait))
        FirestoreClass().getAssignedMembersList(this,mBoardDetails.assignedTo)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_add_member, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.action_add_member ->{
                dialogSearchMember()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun dialogSearchMember(){
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_search_member)
        dialog.findViewById<TextView>(R.id.tv_add).setOnClickListener {
            val email = dialog.findViewById<EditText>(R.id.et_email_search_member).text.toString().lowercase()
            if(email.isNotEmpty() && !checkIfUserAlreadyAssigned(mAssignedMemberList, email)){
                dialog.dismiss()
                showProgressDialog(resources.getString(R.string.please_wait))
                FirestoreClass().getMemberDetails(this, email)
            } else if ( checkIfUserAlreadyAssigned(mAssignedMemberList, email)) {
                showErrorSnackBar("Member already assigned!")
            } else{
                showErrorSnackBar("Please enter email address.")
            }
        }
        dialog.findViewById<TextView>(R.id.tv_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()

    }

    override fun onBackPressed() {
        if(anyChangesMade){
            setResult(Activity.RESULT_OK)
        }
        super.onBackPressed()
    }

    fun memberDetails(user: User){
        mBoardDetails.assignedTo.add(user.id)
        FirestoreClass().assignMemberToBoard(this,mBoardDetails,user)
    }

    fun memberAssignSuccess(user: User){
        hideProgressDialog()
        mAssignedMemberList.add(user)
        setupMembersList(mAssignedMemberList)
        anyChangesMade = true

        SendNotificationToUserAsyncTask(mBoardDetails.name, user.fcmToken).execute()
    }

    fun setupMembersList(list: ArrayList<User>){
        hideProgressDialog()

        mAssignedMemberList = list

        binding?.rvMembersList?.layoutManager = LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false)
        binding?.rvMembersList?.setHasFixedSize(true)
        binding?.rvMembersList?.adapter = MembersListAdapter(this, list)
    }

    private fun setupActionBar() {
        setSupportActionBar(binding?.toolbarMembersActivity)
        binding?.toolbarMembersActivity?.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        binding?.toolbarMembersActivity?.title = resources.getString(R.string.members)
    }

    private inner class SendNotificationToUserAsyncTask( private val boardName: String, private val token: String) {


        fun execute() {
            lifecycleScope.launch(Dispatchers.IO) {
//                delay(5000L)
                val stringResult = makeApiCall(boardName, token)
                afterCallFinish(stringResult)
            }
        }

        private fun makeApiCall(boardName: String, token: String): String {
            var result: String
            var connection: HttpURLConnection? = null

            try {
                val url = URL(Constants.FCM_BASE_URL)
                connection = url.openConnection() as HttpURLConnection?   //Returns a URLConnection instance that represents a connection to the remote object referred to by the URL.
                connection!!.doInput = true  //doInput tells if we get any data(by default doInput will be true and doOutput false)
                connection.doOutput = true //doOutput tells if we send any data with the api call
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"

                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("charset", "utf-8")
                connection.setRequestProperty("Accept", "application/json")

                connection.setRequestProperty(Constants.FCM_AUTHORIZATION, "${Constants.FCM_KEY}=${Constants.FCM_SERVER_KEY}")

                connection.useCaches = false

                val wr = DataOutputStream(connection.outputStream)
                val jsonRequest = JSONObject()
                val dataObject = JSONObject()

                dataObject.put(Constants.FCM_KEY_TITLE, "Assigned to the board $boardName")
                dataObject.put(Constants.FCM_KEY_MESSAGE, "You have been assigned to a new board by ${mAssignedMemberList[0].name}.")

                jsonRequest.put(Constants.FCM_KEY_DATA, dataObject)
                jsonRequest.put(Constants.FCM_KEY_TO, token)

                wr.writeBytes(jsonRequest.toString())
                wr.flush()
                wr.close()

                val httpResult: Int = connection.responseCode
                if (httpResult == HttpURLConnection.HTTP_OK) {
                    //now once we have established a successful connection, we want to read the data.

                    //Returns an input stream that reads from this open connection. A SocketTimeoutException can be thrown when
                    // reading from the returned input stream if the read timeout expires before data is available for read.
                    val inputStream = connection.inputStream

                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val stringBuilder: StringBuilder = StringBuilder()
                    var line: String?
                    try {
                        while (reader.readLine().also { line = it } != null) {
                            stringBuilder.append(line + "\n")
                            Log.i("TAG", "doInBackground: $line\n")
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } finally {
                        try {  //there could be some error while closing the inputStream
                            inputStream.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    result = stringBuilder.toString()
                } else {  //if the response code is not OK
                    result = connection.responseMessage
                }
            } catch (e: SocketTimeoutException) {
                result = "Connection Timeout"
            } catch (e: Exception) {
                result = "Error + ${e.message}"
            } finally {
                connection?.disconnect()
            }

            return result
        }

        private fun afterCallFinish(result: String?) {

            Log.i("JSON RESPONSE RESULT", result.toString())
        }
    }

    private fun checkIfUserAlreadyAssigned(assignedto: ArrayList<User>, email: String): Boolean{
        for(i in assignedto.indices){
            if(assignedto[i].email.lowercase() == email.lowercase()){
                return true
            }
        }
        return false
    }

}

