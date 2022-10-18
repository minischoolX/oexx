package org.edx.mobile.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.ListPopupWindow
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import org.edx.mobile.R
import org.edx.mobile.databinding.FragmentLearnBinding
import org.edx.mobile.module.analytics.Analytics

@AndroidEntryPoint
class LearnFragment : OfflineSupportBaseFragment() {

    private lateinit var binding: FragmentLearnBinding
    private var selectedItemPosition = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLearnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    private fun initViews() {
        binding.llLearnSelection.setOnClickListener {
            showLearnPopupMenu(it)
        }
        updateScreen(0, getString(R.string.label_my_courses)) // select my courses by default
    }

    private fun showLearnPopupMenu(parentView: View) {
        val listPopupWindow = ListPopupWindow(requireContext())
        listPopupWindow.anchorView = parentView
        val items = arrayListOf(getString(R.string.label_my_courses))
        if (environment.config.programConfig.isEnabled) {
            items.add(getString(R.string.label_my_programs))
        }
        val adapter = ArrayAdapter(requireContext(), R.layout.learn_selection_item, items)
        listPopupWindow.setAdapter(adapter)
        listPopupWindow.setOnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
            if (parent?.selectedItemPosition != position) {
                updateScreen(position, adapter.getItem(position))
            }
            listPopupWindow.dismiss()
        }
        listPopupWindow.show()
        listPopupWindow.listView?.postDelayed({
            listPopupWindow.listView?.setSelection(
                selectedItemPosition
            )
        }, 1500)
    }

    private fun updateScreen(itemPosition: Int, title: String?) {
        selectedItemPosition = itemPosition
        val fragment: Fragment = when (itemPosition) {
            0 -> {
                environment.analyticsRegistry.trackScreenView(
                    Analytics.Screens.MY_COURSES
                )
                MyCoursesListFragment().apply {
                    arguments = this@LearnFragment.arguments
                }
            }
            1 -> {
                environment.analyticsRegistry.trackScreenView(
                    Analytics.Screens.MY_PROGRAM
                )
                WebViewProgramFragment.newInstance(environment.config.programConfig.url)
            }
            else -> {
                throw IllegalArgumentException("values must be provided")
            }
        }
        if (childFragmentManager.findFragmentByTag(fragment.javaClass.name) == null) {
            val fragmentTransaction = childFragmentManager.beginTransaction()
            fragmentTransaction.replace(binding.flLearn.id, fragment, fragment.javaClass.name)
            fragmentTransaction.commit()
            title?.let {
                binding.tvSelectedItem.text = it
            }
        }
    }

    override fun isShowingFullScreenError(): Boolean {
        return false
    }
}
