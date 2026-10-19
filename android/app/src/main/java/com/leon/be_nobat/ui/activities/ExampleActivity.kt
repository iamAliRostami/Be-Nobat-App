package com.leon.be_nobat.ui.activities

import com.leon.be_nobat.R
import com.leon.be_nobat.helpers.BaseActivity

class ExampleActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_example

    override fun setupViews() {
        setToolbarTitle(getString(R.string.example_title))
        setMenuVisibility(true)
        setHorizontalButtonsVisibility(false)
    }

    override fun observeViewModel() {
        // مشاهده داده‌ها از ViewModel در اینجا
    }
}
