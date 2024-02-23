/*
package org.oppia.android.app.home.topiclist

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import javax.inject.Inject
import org.oppia.android.app.recyclerview.BindableAdapter
import org.oppia.android.app.shim.ViewBindingShim
import org.oppia.android.app.view.ViewComponentFactory
import org.oppia.android.app.view.ViewComponentImpl

class AllTopicListView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

  @Inject
  lateinit var bindingInterface: ViewBindingShim

  @Inject
  lateinit var singleTypeBuilderFactory: BindableAdapter.SingleTypeBuilder.Factory

  private lateinit var topicList: List<TopicSummaryViewModel>

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    val viewComponentFactory = FragmentManager.findFragment<Fragment>(this) as ViewComponentFactory
    val viewComponent = viewComponentFactory.createViewComponent(this) as ViewComponentImpl
    viewComponent.inject(this)

    bindDataToAdapter()
  }

  private fun bindDataToAdapter() {
    if(adapter == null) {
      adapter = createAdapter()
    }

//    (adapter as BindableAdapter<*>).setDataUnchecked(topicList)
  }

  fun setToastMsg(text: String){
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
  }

  private fun createAdapter(): BindableAdapter<TopicSummaryViewModel> {
    return singleTypeBuilderFactory.create<TopicSummaryViewModel>()
      .registerViewBinder(
        inflateView = {parent ->
          bindingInterface.provideTopicSummaryCardInflatedView(
            LayoutInflater.from(parent.context),
            parent,
            attachToParent = false
          )
        },
        bindView = { view, viewModel ->
          bindingInterface.provideTopicSummaryViewModel(
            view,
            viewModel
          )
        }
      ).build()
  }
}*/
