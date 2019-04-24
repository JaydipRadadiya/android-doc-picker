package com.greentoad.turtlebody.docpicker.ui.components.file


import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager

import com.greentoad.turtlebody.docpicker.R
import com.greentoad.turtlebody.docpicker.core.DocPickerConfig
import com.greentoad.turtlebody.docpicker.core.FileManager
import com.greentoad.turtlebody.docpicker.ui.base.FragmentBase
import com.greentoad.turtlebody.docpicker.ui.components.ActivityLibMain
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.annotations.NonNull
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.tb_doc_picker_fragment_doc_list.*
import kotlinx.android.synthetic.main.tb_doc_picker_frame_progress.*
import org.jetbrains.anko.info
import java.io.File

class DocFragment : FragmentBase(), DocAdapter.OnDocClickListener {

    companion object {

        @JvmStatic
        fun newInstance(key: Int, b: Bundle?): Fragment {
            val bf: Bundle = b ?: Bundle()
            bf.putInt("fragment.key", key);
            val fragment = DocFragment()
            fragment.arguments = bf
            return fragment
        }

        const val B_ARG_FOLDER_PATH = "args.folder.path"
    }

    private var mFolderPath: String = ""

    private var mDocAdapter: DocAdapter = DocAdapter()
    private var mDocModelList: MutableList<DocModel> = arrayListOf()
    var mPickerConfig: DocPickerConfig = DocPickerConfig()
    var mUriList: ArrayList<Uri> = arrayListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.tb_doc_picker_fragment_doc_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        arguments?.let {
            mFolderPath= it.getString(B_ARG_FOLDER_PATH,"")
            mPickerConfig = it.getSerializable(DocPickerConfig.ARG_BUNDLE) as DocPickerConfig
            info { "folderPath: $mFolderPath" }
        }
        initButton()
        initAdapter()

    }

    private fun initButton() {
        if (!mPickerConfig.mAllowMultiImages) {
            ll_bottom_layout.visibility = View.GONE
        }
        tb_doc_picker_btn_done.setOnClickListener {
            getAllUris()
        }

        tb_doc_picker_doc_filter.setOnClickListener {
            (activity as ActivityLibMain).startFragmentCreate()
        }
    }

    private fun getAllUris() {
        for(i in mDocModelList){
            if(i.isSelected){
                mUriList.add(FileManager.getContentUri(context!!, File(i.filePath)))
            }
        }

        if(mUriList.isNotEmpty()){
            (activity as ActivityLibMain).sendBackData(mUriList)
        }
    }


    private fun initAdapter() {
        mDocAdapter.setListener(this)
        mDocAdapter.mShowCheckBox = mPickerConfig.mAllowMultiImages

        tb_doc_picker_fragment_doc_list_recycler_view.layoutManager = LinearLayoutManager(context)
        tb_doc_picker_fragment_doc_list_recycler_view.adapter = mDocAdapter
        fetchDocFolders(mPickerConfig.getCustomExtArgs(mPickerConfig.mUserSelectedDocTypes))
    }

    override fun onDocCheck(pData: DocModel) {
        if(!mPickerConfig.mAllowMultiImages){
            if(mPickerConfig.mShowConfirmationDialog){
                val simpleAlert = AlertDialog.Builder(context!!)
                simpleAlert.setMessage("Are you sure to select ${pData.name}")
                    .setCancelable(false)
                    .setPositiveButton("OK") { dialog, which ->
                        (activity as ActivityLibMain).sendBackData(arrayListOf(FileManager.getContentUri(context!!, File(pData.filePath))))
                    }
                    .setNegativeButton("Cancel") { dialog, which -> dialog.dismiss()  }
                simpleAlert.show()
            }
            else{
                (activity as ActivityLibMain).sendBackData(arrayListOf(FileManager.getContentUri(context!!, File(pData.filePath))))
            }
        }
        else{
            val selectedIndex = mDocModelList.indexOf(pData)

            if(selectedIndex >= 0){
                //toggle
                mDocModelList[selectedIndex].isSelected = !(mDocModelList[selectedIndex].isSelected)
                //update ui
                mDocAdapter.updateIsSelected(mDocModelList[selectedIndex])
            }

            var size = 0
            for(i in mDocModelList){
                if(i.isSelected){
                    size += 1
                }
            }
            (activity as ActivityLibMain).updateCounter(size)
            tb_doc_picker_btn_done.isEnabled = size>0
        }
    }

    fun onFilterDone(list: ArrayList<String>) {
        info { "list: $list" }
        fetchDocFolders(mPickerConfig.getCustomExtArgs(list))
    }

    private fun fetchDocFolders(list: Array<String?>) {
        val fileItems = Single.fromCallable<Boolean> {
            mDocModelList.clear()
            val tempArray = FileManager.getDocFilesInFolder(context!!,mFolderPath)
            for(i in tempArray){
                if(File(i.filePath).length()>0){
                    for(j in list){
                        if(File(i.filePath).extension == (j!!.substring(2))){
                            mDocModelList.add(i)
                            info { "added" }
                            info { "j: $j\ni: $i" }
                        }
                    }


                }
            }

            info { "added all: ${mDocModelList}" }

            true
        }

        fileItems.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : SingleObserver<Boolean> {
                override fun onSubscribe(@NonNull d: Disposable) {
                    frame_progress.visibility = View.VISIBLE
                }

                override fun onSuccess(t: Boolean) {
                    mDocAdapter.setData(mDocModelList)
                    frame_progress.visibility = View.GONE
                }

                override fun onError(@NonNull e: Throwable) {
                    frame_progress.visibility = View.GONE
                    info { "error: ${e.message}" }
                }
            })
    }

}