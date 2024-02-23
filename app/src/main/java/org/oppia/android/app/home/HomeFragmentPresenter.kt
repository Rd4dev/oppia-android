package org.oppia.android.app.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.oppia.android.R
import org.oppia.android.app.drawer.NAVIGATION_PROFILE_ID_ARGUMENT_KEY
import org.oppia.android.app.fragment.FragmentScope
import org.oppia.android.app.home.promotedlist.ComingSoonTopicListViewModel
import org.oppia.android.app.home.promotedlist.PromotedStoryListViewModel
import org.oppia.android.app.home.topiclist.AllTopicsViewModel
import org.oppia.android.app.home.topiclist.TopicSummaryViewModel
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.model.TopicSummary
import org.oppia.android.app.recyclerview.BindableAdapter
import org.oppia.android.app.translation.AppLanguageResourceHandler
import org.oppia.android.app.utility.datetime.DateTimeUtil
import org.oppia.android.databinding.AllTopicsBinding
import org.oppia.android.databinding.ComingSoonTopicListBinding
import org.oppia.android.databinding.HomeFragmentBinding
import org.oppia.android.databinding.PromotedStoryListBinding
import org.oppia.android.databinding.TopicSummaryViewBinding
import org.oppia.android.databinding.WelcomeBinding
import org.oppia.android.domain.oppialogger.OppiaLogger
import org.oppia.android.domain.oppialogger.analytics.AnalyticsController
import org.oppia.android.domain.profile.ProfileManagementController
import org.oppia.android.domain.topic.TopicListController
import org.oppia.android.domain.translation.TranslationController
import org.oppia.android.util.parser.html.StoryHtmlParserEntityType
import org.oppia.android.util.parser.html.TopicHtmlParserEntityType
import javax.inject.Inject
import org.oppia.android.databinding.AllTopicsListBinding

/** The presenter for [HomeFragment]. */
@FragmentScope
class HomeFragmentPresenter @Inject constructor(
  private val activity: AppCompatActivity,
  private val fragment: Fragment,
  private val profileManagementController: ProfileManagementController,
  private val topicListController: TopicListController,
  private val oppiaLogger: OppiaLogger,
  private val analyticsController: AnalyticsController,
  @TopicHtmlParserEntityType private val topicEntityType: String,
  @StoryHtmlParserEntityType private val storyEntityType: String,
  private val resourceHandler: AppLanguageResourceHandler,
  private val dateTimeUtil: DateTimeUtil,
  private val translationController: TranslationController,
  private val multiTypeBuilderFactory: BindableAdapter.MultiTypeBuilder.Factory
) {
  private val routeToTopicPlayStoryListener = activity as RouteToTopicPlayStoryListener
  private lateinit var binding: HomeFragmentBinding
  private var internalProfileId: Int = -1

  fun handleCreateView(inflater: LayoutInflater, container: ViewGroup?): View? {
    binding = HomeFragmentBinding.inflate(inflater, container, /* attachToRoot= */ false)
    // NB: Both the view model and lifecycle owner must be set in order to correctly bind LiveData elements to
    // data-bound view models.

    internalProfileId = activity.intent.getIntExtra(NAVIGATION_PROFILE_ID_ARGUMENT_KEY, -1)
    logHomeActivityEvent()

    val homeViewModel = HomeViewModel(
      activity,
      fragment,
      oppiaLogger,
      internalProfileId,
      profileManagementController,
      topicListController,
      topicEntityType,
      storyEntityType,
      resourceHandler,
      dateTimeUtil,
      translationController
    )

    // Create separate RecyclerView for TOPIC_LIST with horizontal orientation
    /*val topicListLayoutManager = LinearLayoutManager(activity.applicationContext, LinearLayoutManager.HORIZONTAL, false)
    val topicListAdapter = createTopicListAdapter()*/

    /*binding.topicListRecyclerView.apply {
      adapter = topicListAdapter
      layoutManager = topicListLayoutManager
    }*/

//    val topicListAdapter = createTopicListAdapter()

    val homeAdapter = createRecyclerViewAdapter()
    val spanCount = activity.resources.getInteger(R.integer.home_span_count)
//    val spanCount = 3
    val homeLayoutManager = GridLayoutManager(activity.applicationContext, spanCount)
    homeLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
      override fun getSpanSize(position: Int): Int {
        return if (position < homeAdapter.itemCount &&
          homeAdapter.getItemViewType(position) == ViewType.TOPIC_LIST.ordinal
        ) 1
        else spanCount
      }
    }

    /*homeLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
      override fun getSpanSize(position: Int): Int {
        return 2
      }
    }*/

//    val homeLayoutManager2 = LinearLayoutManager(activity.applicationContext)
    /*homeLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
      override fun getSpanSize(position: Int): Int {
        return if (position < homeAdapter.itemCount &&
          homeAdapter.getItemViewType(position) == ViewType.TOPIC_LIST.ordinal
        ) 1
        else spanCount
      }
    }*/

//    val homeLayoutManager = LinearLayoutManager(activity.applicationContext, LinearLayoutManager.HORIZONTAL, false)

    /*if (homeAdapter.getItemViewType(0) == ViewType.ALL_TOPICS.ordinal) {
      val layoutManager =
        LinearLayoutManager(activity.applicationContext, RecyclerView.HORIZONTAL, false)
      binding.homeRecyclerView.layoutManager = layoutManager
    } else {
      val spanCount = 3
      val layoutManager = GridLayoutManager(activity.applicationContext, spanCount)
      layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
          return 2
        }
      }
      binding.homeRecyclerView.layoutManager = layoutManager
    }*/

    binding.homeRecyclerView.apply {
      adapter = homeAdapter
      layoutManager = homeLayoutManager
    }

    /*binding.topicListRecyclerView.apply {
      adapter = topicListAdapter
      layoutManager = homeLayoutManager2
    }*/

    binding.let {
      it.lifecycleOwner = fragment
      it.viewModel = homeViewModel
    }

    return binding.root
  }

  private fun createRecyclerViewAdapter(): BindableAdapter<HomeItemViewModel> {
    return multiTypeBuilderFactory.create<HomeItemViewModel, ViewType> { viewModel ->
      when (viewModel) {
        is WelcomeViewModel -> ViewType.WELCOME_MESSAGE
        is PromotedStoryListViewModel -> ViewType.PROMOTED_STORY_LIST
        is ComingSoonTopicListViewModel -> ViewType.COMING_SOON_TOPIC_LIST
        is AllTopicsViewModel -> ViewType.ALL_TOPICS
        is TopicSummaryViewModel -> ViewType.TOPIC_LIST
        else -> throw IllegalArgumentException("Encountered unexpected view model: $viewModel")
      }
    }

      .registerViewDataBinder(
        viewType = ViewType.WELCOME_MESSAGE,
        inflateDataBinding = WelcomeBinding::inflate,
        setViewModel = WelcomeBinding::setViewModel,
        transformViewModel = { it as WelcomeViewModel }
      )
      .registerViewDataBinder(
        viewType = ViewType.PROMOTED_STORY_LIST,
        inflateDataBinding = PromotedStoryListBinding::inflate,
        setViewModel = PromotedStoryListBinding::setViewModel,
        transformViewModel = { it as PromotedStoryListViewModel }
      )
      .registerViewDataBinder(
        viewType = ViewType.COMING_SOON_TOPIC_LIST,
        inflateDataBinding = ComingSoonTopicListBinding::inflate,
        setViewModel = ComingSoonTopicListBinding::setViewModel,
        transformViewModel = { it as ComingSoonTopicListViewModel }
      )
      .registerViewDataBinder(
        viewType = ViewType.ALL_TOPICS,
        inflateDataBinding = AllTopicsBinding::inflate,
        setViewModel = AllTopicsBinding::setViewModel,
        transformViewModel = { it as AllTopicsViewModel }
      )
      .registerViewDataBinder(
        viewType = ViewType.TOPIC_LIST,
        inflateDataBinding = TopicSummaryViewBinding::inflate,
        setViewModel = TopicSummaryViewBinding::setViewModel,
        transformViewModel = {it as TopicSummaryViewModel}

      )
      /*.registerViewDataBinder(
        viewType = ViewType.TOPIC_LISTED,
        inflateDataBinding = AllTopicsListBinding::inflate,
        setViewModel = AllTopicsListBinding::setViewModel,
        transformViewModel = {it as TopicSummaryViewModel}

      )*/
      .build()
      /*.apply {
        // Check if the adapter contains the ALL_TOPICS view type and set a LinearLayoutManager for it
        if (getItemViewType(0) == ViewType.ALL_TOPICS.ordinal) {
          val layoutManager =
            LinearLayoutManager(activity.applicationContext, RecyclerView.HORIZONTAL, false)
          binding.homeRecyclerView.layoutManager = layoutManager
        } else {
          val spanCount = 3
          val layoutManager = GridLayoutManager(activity.applicationContext, spanCount)
          layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
              return if (position < itemCount &&
                getItemViewType(position) == ViewType.TOPIC_LIST.ordinal
              ) 1
              else spanCount
            }
          }
          binding.homeRecyclerView.layoutManager = layoutManager
        }
      }*/
  }

  /*private fun createTopicListAdapter(): BindableAdapter<HomeItemViewModel> {
    return multiTypeBuilderFactory.create<HomeItemViewModel, ViewType> { viewModel ->
      when (viewModel) {
        is TopicSummaryViewModel -> ViewType.TOPIC_LIST
        else -> throw IllegalArgumentException("Encountered unexpected view model: $viewModel")
      }
    }
      .registerViewDataBinder(
        viewType = ViewType.TOPIC_LIST,
        inflateDataBinding = TopicSummaryViewBinding::inflate,
        setViewModel = TopicSummaryViewBinding::setViewModel,
        transformViewModel = { it as TopicSummaryViewModel }
      )
      .build()
  }*/

  private enum class ViewType {
    WELCOME_MESSAGE,
    PROMOTED_STORY_LIST,
    COMING_SOON_TOPIC_LIST,
    ALL_TOPICS,
    TOPIC_LIST,
    TOPIC_LISTED
  }

  fun onTopicSummaryClicked(topicSummary: TopicSummary) {
    routeToTopicPlayStoryListener.routeToTopicPlayStory(
      internalProfileId,
      topicSummary.topicId,
      topicSummary.firstStoryId
    )
  }

  private fun logHomeActivityEvent() {
    analyticsController.logImportantEvent(
      oppiaLogger.createOpenHomeContext(),
      ProfileId.newBuilder().apply { internalId = internalProfileId }.build()
    )
  }
}
