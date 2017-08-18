package com.example.chenyi.android_training.ui

import android.app.ActivityOptions
import android.app.FragmentManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.PersistableBundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.util.LogHelper
import org.jetbrains.anko.find

/**
 * 带有 toolbar, navigation drawer and cast support 的 Abstract activity。
 * 作为顶级的 Activity 被所有需要展示的 Activity 继承
 */
abstract class ActionBarActivity : AppCompatActivity() {

    private lateinit var mToolbar: Toolbar
    private var mDrawerLayout: DrawerLayout? = null
    private var mDrawerToggle: ActionBarDrawerToggle? = null

    private var mToolbarInitialized: Boolean = false

    private var mItemToOpenWhenDrawerCloses = -1

    private val mDrawerListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerClosed(drawerView: View) {
            mDrawerToggle?.onDrawerClosed(drawerView)
            if (mItemToOpenWhenDrawerCloses >= 0) {
                val extras = ActivityOptions.makeCustomAnimation(
                        this@ActionBarActivity, R.anim.fade_in, R.anim.fade_out).toBundle()

                val activityClass: Class<*>? = when (mItemToOpenWhenDrawerCloses) {
                    R.id.navigation_allmusic -> MusicPlayerActivity::class.java
//                    R.id.navigation_playlists -> PlaceholderActivity::class.java
                    else -> null
                }
                if (activityClass != null && activityClass != this@ActionBarActivity.javaClass) {
                    startActivity(Intent(this@ActionBarActivity, activityClass), extras)
                    finish()
                }
            }
        }

        override fun onDrawerStateChanged(newState: Int) {
            mDrawerToggle?.onDrawerStateChanged(newState)
        }

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            mDrawerToggle?.onDrawerSlide(drawerView, slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            mDrawerToggle?.onDrawerOpened(drawerView)
            supportActionBar?.title = getString(R.string.app_name)
        }
    }

    private val mBackStackChangedListener = FragmentManager.OnBackStackChangedListener { updateDrawerToggle() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "Activity onCreate")
    }

    override fun onStart() {
        super.onStart()
        if (!mToolbarInitialized) {
            throw IllegalStateException("在 onCreate 方法结束前初始化 Toolbar")
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        mDrawerToggle?.syncState()
    }

    public override fun onResume() {
        super.onResume()

        // 当 fragment 返回堆栈时，我们可能需要更新 action bar toggle
        fragmentManager.addOnBackStackChangedListener(mBackStackChangedListener)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onPause() {
        super.onPause()

        fragmentManager.removeOnBackStackChangedListener(mBackStackChangedListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.drawer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (mDrawerToggle?.onOptionsItemSelected(item) == true) {
            return true
        }
        // 如果不是由 drawer 来处理，将需要通过回到以前来处理
        if (item?.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        // 如果 drawer 打开, back 将会关闭它
        if (mDrawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            mDrawerLayout?.closeDrawers()
            return
        }
        // 否则，它可能返回到以前的 fragment
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
        } else {
            // 最后，它将依赖于系统的行为
            super.onBackPressed()
        }
    }

    override fun setTitle(title: CharSequence) {
        super.setTitle(title)
        mToolbar.title = title
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        mToolbar.setTitle(titleId)
    }

    protected fun initializeToolbar() {
        mToolbar = find(R.id.toolbar)

        mToolbar.inflateMenu(R.menu.drawer)

        mDrawerLayout = find(R.id.drawer_layout)
        if (mDrawerLayout != null) {
            val navigationView: NavigationView = find(R.id.nav_view)

            // 创建一个 ActionBarDrawerToggle 控制 drawer 的打开和关闭:
            mDrawerToggle = ActionBarDrawerToggle(this, mDrawerLayout,
                    mToolbar, R.string.open_content_drawer, R.string.close_content_drawer)
            mDrawerLayout?.setDrawerListener(mDrawerListener)
            populateDrawerItems(navigationView)
            setSupportActionBar(mToolbar)
            updateDrawerToggle()
        } else {
            setSupportActionBar(mToolbar)
        }

        mToolbarInitialized = true
    }

    private fun populateDrawerItems(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            menuItem.isChecked = true
            mItemToOpenWhenDrawerCloses = menuItem.itemId
            mDrawerLayout?.closeDrawers()
            true
        }
        if (MusicPlayerActivity::class.java!!.isAssignableFrom(javaClass)) {
            navigationView.setCheckedItem(R.id.navigation_allmusic)
        }
//        else if (PlaceholderActivity::class.java!!.isAssignableFrom(javaClass)) {
//            navigationView.setCheckedItem(R.id.navigation_playlists)
//        }
    }

    protected fun updateDrawerToggle() {
        if (mDrawerToggle == null) {
            return
        }
        val isRoot = fragmentManager.backStackEntryCount == 0
        mDrawerToggle?.isDrawerIndicatorEnabled = isRoot

        supportActionBar?.setDisplayShowHomeEnabled(!isRoot)
        supportActionBar?.setDisplayHomeAsUpEnabled(!isRoot)
        supportActionBar?.setHomeButtonEnabled(!isRoot)

        if (isRoot) {
            mDrawerToggle?.syncState()
        }
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(ActionBarActivity::class.java)
    }
}
