package org.oppia.android.app.topic.revisioncard

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import org.oppia.android.app.fragment.FragmentScope
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.topic.conceptcard.ConceptCardFragment
import org.oppia.android.app.translation.AppLanguageResourceHandler
import org.oppia.android.databinding.RevisionCardFragmentBinding
import org.oppia.android.domain.oppialogger.OppiaLogger
import org.oppia.android.domain.oppialogger.analytics.AnalyticsController
import org.oppia.android.domain.translation.TranslationController
import org.oppia.android.util.gcsresource.DefaultResourceBucketName
import org.oppia.android.util.parser.html.HtmlParser
import org.oppia.android.util.parser.html.TopicHtmlParserEntityType
import javax.inject.Inject
import org.oppia.android.app.model.ExplorationFragmentArguments
import org.oppia.android.app.model.Profile
import org.oppia.android.app.model.ReadingTextSize
import org.oppia.android.app.model.ReadingTextSizeActivityParams
import org.oppia.android.app.options.OptionsActivity
import org.oppia.android.app.options.OptionsActivityPresenter
import org.oppia.android.app.options.ReadingTextSizeActivityPresenter
import org.oppia.android.app.player.exploration.DefaultFontSizeStateListener
import org.oppia.android.app.player.exploration.ExplorationFragmentPresenter
import org.oppia.android.app.profile.ProfileChooserActivity
import org.oppia.android.app.utility.FontScaleConfigurationUtil
import org.oppia.android.domain.profile.ProfileManagementController
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProviders.Companion.toLiveData
import org.oppia.android.util.extensions.getProto

/** Presenter for [RevisionCardFragment], sets up bindings from ViewModel. */
@FragmentScope
class RevisionCardFragmentPresenter @Inject constructor(
  private val fragment: Fragment,
  private val activity: AppCompatActivity,
  private val profileManagementController: ProfileManagementController,
  private val fontScaleConfigurationUtil: FontScaleConfigurationUtil,
  private val oppiaLogger: OppiaLogger,
  private val analyticsController: AnalyticsController,
  private val htmlParserFactory: HtmlParser.Factory,
  @DefaultResourceBucketName private val resourceBucketName: String,
  @TopicHtmlParserEntityType private val entityType: String,
  private val translationController: TranslationController,
  private val appLanguageResourceHandler: AppLanguageResourceHandler,
  private val revisionCardViewModelFactory: RevisionCardViewModel.Factory
) : HtmlParser.CustomOppiaTagActionListener {
  private lateinit var profileId: ProfileId

  fun handleCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    topicId: String,
    subtopicId: Int,
    profileId: ProfileId,
    subtopicListSize: Int
  ): View? {
    this.profileId = profileId

    val binding =
      RevisionCardFragmentBinding.inflate(
        inflater,
        container,
        /* attachToRoot= */ false
      )
    val view = binding.revisionCardExplanationText
    val viewModel = revisionCardViewModelFactory.create(
      topicId,
      subtopicId,
      profileId,
      subtopicListSize
    )

    logRevisionCardEvent(topicId, subtopicId)

    binding.let {
      it.viewModel = viewModel
      it.lifecycleOwner = fragment
    }

    viewModel.revisionCardLiveData.observe(
      fragment
    ) { ephemeralRevisionCard ->
      val pageContentsHtml =
        translationController.extractString(
          ephemeralRevisionCard.revisionCard.pageContents,
          ephemeralRevisionCard.writtenTranslationContext
        )
      view.text = htmlParserFactory.create(
        resourceBucketName, entityType, topicId, imageCenterAlign = true,
        customOppiaTagActionListener = this,
        displayLocale = appLanguageResourceHandler.getDisplayLocale()
      ).parseOppiaHtml(
        pageContentsHtml, view, supportsLinks = true, supportsConceptCards = true
      )

      val profileDataProvider = profileManagementController.getProfile(profileId)
      profileDataProvider.toLiveData().observe(
        fragment
      ) { result ->
        val readingTextSize = retrieveArguments().readingTextSize
        Log.d("readingprofiledata", "handleCreateView: Profile data provider - $profileDataProvider")
        Log.d("readingprofiledata", "handleCreateView: Reading profile data provider - $profileManagementController")
        Log.d("readingprofiledata", "handleCreateView: Reading text size in profile data provider - $readingTextSize")
      }

//      val redtrans = Transformations.map(profileManagementController.getProfile(profileId).toLiveData(), ::processReadingTextSizeResult)


      profileDataProvider.toLiveData().observe(
        fragment
      ) { reresult ->
        val redproc = retrieveReadingTextSize().observe(
          fragment,
          { result ->
            val readingTextSizeinex = retrieveArguments().readingTextSize
//            if (result != readingTextSize) {
            if (reresult is AsyncResult.Success) {
              if (reresult.value.readingTextSize != readingTextSizeinex) {
                fontScaleConfigurationUtil.adjustFontScale(
                  fragment.requireActivity(),
                  reresult.value.readingTextSize
                )
                Log.d("callingrecreate", "Calling recreate in rev - $result")
                Log.d("callingrecreate", "Calling recreate in rerev - $reresult")
                Log.d("callingrecreate", "Calling recreate in retr - $readingTextSizeinex")
//                fragment.requireActivity().recreate()
              }
            }
//            }
            Log.d(
              "readingprofiledata",
              "handleCreateView: Reading text size result in readproc - $result"
            )
            result
          }
        )
        Log.d(
          "readingprofiledata",
          "handleCreateView: Reading text size in profile data process - $redproc"
        )
      }


//      fontScaleConfigurationUtil.adjustFontScale(fragment.requireActivity(), ReadingTextSize.EXTRA_LARGE_TEXT_SIZE)
//      view.textSize = 42f

//      fragment.requireActivity().recreate()

      //text size
      val redtex : ReadingTextSize
      /*val red = ReadingTextSizeActivityParams.newBuilder().apply {
        redtex = readingTextSize
      }*/

//      val red = Profile.newBuilder().readingTextSizeValue
//      val red = ReadingTextSizeActivityPresenter(RevisionCardActivity as AppCompatActivity).getSelectedReadingTextSize()

//      Log.d("readingtextsizer", "handleCreateView: Reading text size - $red")
    }

    return binding.root
  }

  private fun retrieveArguments(): ExplorationFragmentArguments {
    return fragment.requireArguments().getProto(
      ExplorationFragmentPresenter.ARGUMENTS_KEY, ExplorationFragmentArguments.getDefaultInstance()
    )
  }

  /** Dismisses the concept card fragment if it's currently active in this fragment. */
  fun dismissConceptCard() {
    ConceptCardFragment.dismissAll(fragment.childFragmentManager)
  }

  private fun logRevisionCardEvent(topicId: String, subTopicId: Int) {
    analyticsController.logImportantEvent(
      oppiaLogger.createOpenRevisionCardContext(topicId, subTopicId),
      profileId
    )
  }

  override fun onConceptCardLinkClicked(view: View, skillId: String) {
    ConceptCardFragment.bringToFrontOrCreateIfNew(skillId, profileId, fragment.childFragmentManager)
  }

  private fun retrieveReadingTextSize(): LiveData<ReadingTextSize> {
    return Transformations.map(
      profileManagementController.getProfile(profileId).toLiveData(),
      ::processReadingTextSizeResult
    )
  }

  fun processReadingTextSizeResult(
    readingTextSizeResult: AsyncResult<Profile>
  ): ReadingTextSize {
    return when (readingTextSizeResult) {
      is AsyncResult.Failure -> {
        Log.e(
          "ExplorationManagerFrag", "Failed to retrieve profile", readingTextSizeResult.error
        )
        Profile.getDefaultInstance()
      }
      is AsyncResult.Pending -> Profile.getDefaultInstance()
      is AsyncResult.Success -> readingTextSizeResult.value
    }.readingTextSize
  }
}




