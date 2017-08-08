package com.example.chenyi.android_training

import android.app.FragmentManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v17.leanback.app.BrowseFragment
import com.example.chenyi.android_training.util.LogHelper
import android.support.v17.leanback.app.BackgroundManager
import android.graphics.drawable.Drawable
import android.graphics.Movie
import android.support.v17.leanback.widget.*


class MainActivity : AppCompatActivity() {

    private var mBrowseFragment: BrowseFragment? = null
    private lateinit var mRowsAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fragManager: FragmentManager = fragmentManager
        mBrowseFragment = fragManager.findFragmentById(R.id.browse_fragment) as BrowseFragment?

        mBrowseFragment ?: throw IllegalStateException("Mising fragment with id 'controls'. Cannot continue.")

        // Set display parameters for the BrowseFragment
        mBrowseFragment?.headersState = BrowseFragment.HEADERS_ENABLED
        mBrowseFragment?.title = getString(R.string.app_name)
        mBrowseFragment?.badgeDrawable = resources.getDrawable(R.mipmap.ic_launcher)
        //mBrowseFragment.browseParams(params)
        buildRowsAdapter()
    }

    private fun buildRowsAdapter() {
        mRowsAdapter = ArrayObjectAdapter(ListRowPresenter())

        for (i in 1..4) {
            val listRowAdapter: ArrayObjectAdapter = ArrayObjectAdapter(StringPresenter())
            listRowAdapter.add("Media Item 1")
            listRowAdapter.add("Media Item 2")
            listRowAdapter.add("Media Item 3")
            val header: HeaderItem = HeaderItem(i.toLong(), "Category" + i)
            mRowsAdapter.add(ListRow(header, listRowAdapter))
        }
        mBrowseFragment?.adapter = mRowsAdapter
    }

    private fun updateBackground(drawable: Drawable) {
        BackgroundManager.getInstance(this).drawable = drawable
    }

    private fun clearBackground() {
        BackgroundManager.getInstance(this).drawable = resources.getDrawable(R.mipmap.ic_launcher)
    }

    protected fun getDefaultItemViewSelectedListener(): OnItemViewSelectedListener {
        return OnItemViewSelectedListener { itemViewHolder, item, rowViewHolder, row ->
            if (item is Movie) {
                TODO()
            } else {
                clearBackground()
            }
        }
    }


    companion object {
        private val TAG = LogHelper.makeLogTag(MainActivity::class.java)
    }
}
