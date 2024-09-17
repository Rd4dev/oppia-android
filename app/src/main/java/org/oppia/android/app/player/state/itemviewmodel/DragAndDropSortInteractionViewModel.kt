package org.oppia.android.app.player.state.itemviewmodel

import android.util.Log
import androidx.annotation.StringRes
import androidx.databinding.Observable
import androidx.databinding.ObservableField
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import org.oppia.android.R
import org.oppia.android.app.model.AnswerErrorCategory
import org.oppia.android.app.model.Interaction
import org.oppia.android.app.model.InteractionObject
import org.oppia.android.app.model.ListOfSetsOfHtmlStrings
import org.oppia.android.app.model.ListOfSetsOfTranslatableHtmlContentIds
import org.oppia.android.app.model.SetOfTranslatableHtmlContentIds
import org.oppia.android.app.model.StringList
import org.oppia.android.app.model.SubtitledHtml
import org.oppia.android.app.model.TranslatableHtmlContentId
import org.oppia.android.app.model.UserAnswer
import org.oppia.android.app.model.UserAnswerState
import org.oppia.android.app.model.WrittenTranslationContext
import org.oppia.android.app.player.state.answerhandling.InteractionAnswerErrorOrAvailabilityCheckReceiver
import org.oppia.android.app.player.state.answerhandling.InteractionAnswerHandler
import org.oppia.android.app.player.state.answerhandling.InteractionAnswerReceiver
import org.oppia.android.app.recyclerview.BindableAdapter
import org.oppia.android.app.recyclerview.OnDragEndedListener
import org.oppia.android.app.recyclerview.OnItemDragListener
import org.oppia.android.app.translation.AppLanguageResourceHandler
import org.oppia.android.domain.translation.TranslationController
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.internal.wait
import org.oppia.android.app.model.EphemeralState
import org.oppia.android.domain.exploration.ExplorationProgressController
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProviders.Companion.toLiveData

/** Represents the type of errors that can be thrown by drag and drop sort interaction. */
enum class DragAndDropSortInteractionError(@StringRes private var error: Int?) {
  VALID(error = null),
  EMPTY_INPUT(error = R.string.drag_and_drop_interaction_empty_input);

  /**
   * Returns the string corresponding to this error's string resources, or null if there is none.
   */
  fun getErrorMessageFromStringRes(resourceHandler: AppLanguageResourceHandler): String? =
    error?.let(resourceHandler::getStringInLocale)
}

/** [StateItemViewModel] for drag drop & sort choice list. */
class DragAndDropSortInteractionViewModel private constructor(
  val entityId: String,
  val hasConversationView: Boolean,
  interaction: Interaction,
  private val interactionAnswerErrorOrAvailabilityCheckReceiver: InteractionAnswerErrorOrAvailabilityCheckReceiver, // ktlint-disable max-line-length
  val isSplitView: Boolean,
  private val writtenTranslationContext: WrittenTranslationContext,
  private val resourceHandler: AppLanguageResourceHandler,
  private val translationController: TranslationController,
  userAnswerState: UserAnswerState,
  fragment: Fragment,
  private val explorationProgressController: ExplorationProgressController
) : StateItemViewModel(ViewType.DRAG_DROP_SORT_INTERACTION),
  InteractionAnswerHandler,
  OnItemDragListener,
  OnDragEndedListener {

  private lateinit var contid: Map<String, String>
  private lateinit var contidlist: String
  private lateinit var conhtmllist: String

  private val ephemeralStateLiveData2: LiveData<AsyncResult<EphemeralState>> by lazy {
    explorationProgressController.getCurrentState().toLiveData()
  }

  /*var ephState = ephemeralStateLiveData2.observe(
    fragment
  ) { result ->
    processEphemeralStateResult(result)
  }*/

//  val sub = subscribeToCurrentState()

  /*fun subscribeToCurrentState(): EphemeralState? {
    var eph: EphemeralState? = null
    ephemeralStateLiveData2.observe(
      fragment
    ) { result ->
      eph = processEphemeralStateResult(result)
    }
    return eph
  }*/

  /*fun processEphemeralStateResult(result: AsyncResult<EphemeralState>) {
    when (result) {
      is AsyncResult.Failure -> null
      is AsyncResult.Pending -> {} // Display nothing until a valid result is available.
      is AsyncResult.Success -> {
       //(result.value)
        val sssize = result.value.pendingState.wrongAnswerList
          ?.lastOrNull()
          ?.userAnswer
          ?.answer
          ?.listOfSetsOfTranslatableHtmlContentIds
          ?.contentIdListsList
          ?.size

        for (i in 0 until sssize!!) {
          Log.d("callinginitlogging", "processEphemeralStateResult: $i")
          Log.d("callinginitlogging", "processEphemeralStateResult: $i")
        }
      }
    }
  }*/

  /*fun checkHasPreviousState(ephemeralState: EphemeralState): EphemeralState {
//    Log.d("haspreviousstate", "checkHasPreviousState: Drag and Drop has ephemeral State? - ${ephemeralState.pendingState.wrongAnswerList}")
    Log.d("haspreviousstate", "checkHasPreviousState: Drag and Drop has ephemeral State? - ${ephemeralState.pendingState.wrongAnswerList[0].userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList}")
    Log.d("haspreviousstate", "checkHasPreviousState: Drag and Drop has ephemeral State? - ${ephemeralState.pendingState.wrongAnswerList[0].userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList[0].contentIdsList[0].contentId}")
    return ephemeralState
  }*/

  private val allowMultipleItemsInSamePosition: Boolean by lazy {
    interaction.customizationArgsMap["allowMultipleItemsInSamePosition"]?.boolValue ?: false
  }
  private val choiceSubtitledHtmls: List<SubtitledHtml> by lazy {
    interaction.customizationArgsMap["choices"]
      ?.schemaObjectList
      ?.schemaObjectList
      ?.map { schemaObject -> schemaObject.customSchemaValue.subtitledHtml }
      ?: listOf()
  }

  // original
  private val contentIdHtmlMap: Map<String, String> =
    choiceSubtitledHtmls.associate { subtitledHtml ->
      val translatedHtml =
        translationController.extractString(subtitledHtml, writtenTranslationContext)
      subtitledHtml.contentId to translatedHtml
    }

  /*private val contentIdHtmlMap: Map<String, String> =
    mapOf(Pair("ca_choices_0", "hi"), Pair("ca_choices_1", "bye"), Pair("ca_choices_2", "chao"), Pair("ca_choices_3", "tata"))*/

  private var answerErrorCetegory: AnswerErrorCategory = AnswerErrorCategory.NO_ERROR

  private val _originalChoiceItems: MutableList<DragDropInteractionContentViewModel> =
    computeOriginalChoiceItems(contentIdHtmlMap, choiceSubtitledHtmls, this, resourceHandler, fragment, explorationProgressController)

  private val _choiceItems = computeSelectedChoiceItems(
    contentIdHtmlMap,
    choiceSubtitledHtmls,
    this,
    resourceHandler,
    userAnswerState
  )
  val choiceItems: List<DragDropInteractionContentViewModel> = _choiceItems

  private var pendingAnswerError: String? = null
  private val isAnswerAvailable = ObservableField(false)
  var errorMessage = ObservableField<String>("")

  init {
    Log.d("callinginit", "Calling init")

    /*ephemeralStateLiveData2.observe(
      fragment
    ) { result ->
      processEphemeralStateResult(result)
    }*/

    Log.d("callinginit", "After init")
    val callback: Observable.OnPropertyChangedCallback =
      object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
          Log.d("draganddrop", "onPropertyChanged: Property changed")
          interactionAnswerErrorOrAvailabilityCheckReceiver.onPendingAnswerErrorOrAvailabilityCheck(
            pendingAnswerError,
            inputAnswerAvailable = true // Allow submission without arranging or merging items.
          )
        }
      }
    isAnswerAvailable.addOnPropertyChangedCallback(callback)
    errorMessage.addOnPropertyChangedCallback(callback)

    // Initializing with default values so that submit button is enabled by default.
    interactionAnswerErrorOrAvailabilityCheckReceiver.onPendingAnswerErrorOrAvailabilityCheck(
      pendingAnswerError = null,
      inputAnswerAvailable = true
    )
    checkPendingAnswerError(userAnswerState.answerErrorCategory)
  }

  override fun onItemDragged(
    indexFrom: Int,
    indexTo: Int,
    adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
  ) {
    val item = _choiceItems[indexFrom]
    Log.d("draganddrop", "onItemDragged: $item")
    _choiceItems.removeAt(indexFrom)
    _choiceItems.add(indexTo, item)
    Log.d("draganddrop", "onItemDragged: original choice items - $_originalChoiceItems")
    Log.d("draganddrop", "onItemDragged: choice items - $_choiceItems")

    // Update the data of item moved for every drag if merge icons are displayed.
    if (allowMultipleItemsInSamePosition) {
      _choiceItems[indexFrom].itemIndex = indexFrom
      _choiceItems[indexTo].itemIndex = indexTo
    }
    adapter.notifyItemMoved(indexFrom, indexTo)
  }

  override fun onDragEnded(adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
    // Update the data list if once drag is complete and merge icons are displayed.
    if (allowMultipleItemsInSamePosition) {
      (adapter as BindableAdapter<*>).setDataUnchecked(_choiceItems)
    }
    checkPendingAnswerError(AnswerErrorCategory.REAL_TIME)
  }

  fun onItemMoved(
    indexFrom: Int,
    indexTo: Int,
    adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
  ) {
    val item = _choiceItems[indexFrom]
    _choiceItems.removeAt(indexFrom)
    _choiceItems.add(indexTo, item)

    _choiceItems[indexFrom].itemIndex = indexFrom
    _choiceItems[indexTo].itemIndex = indexTo

    (adapter as BindableAdapter<*>).setDataUnchecked(_choiceItems)
  }

  override fun getPendingAnswer(): UserAnswer = UserAnswer.newBuilder().apply {
    val selectedLists = _choiceItems.map { it.htmlContent }
    val userStringLists = _choiceItems.map { it.computeStringList() }
    Log.d("draganddrop", "getPendingAnswer: Selected Lists - $selectedLists")
    Log.d("draganddrop", "getPendingAnswer: user String Lists - $userStringLists")
    listOfHtmlAnswers = convertItemsToAnswer(userStringLists)
    answer = InteractionObject.newBuilder().apply {
      listOfSetsOfTranslatableHtmlContentIds =
        ListOfSetsOfTranslatableHtmlContentIds.newBuilder().apply {
          addAllContentIdLists(selectedLists)
        }.build()
    }.build()
    this.writtenTranslationContext =
      this@DragAndDropSortInteractionViewModel.writtenTranslationContext
  }.build()

  /**
   * It checks the pending error for the current drag and drop sort interaction, and correspondingly
   * updates the error string based on the specified error category.
   */
  override fun checkPendingAnswerError(category: AnswerErrorCategory): String? {
    answerErrorCetegory = category
    pendingAnswerError = when (category) {
      AnswerErrorCategory.REAL_TIME -> null
      AnswerErrorCategory.SUBMIT_TIME ->
        getSubmitTimeError().getErrorMessageFromStringRes(resourceHandler)
      else -> null
    }
    errorMessage.set(pendingAnswerError)
    return pendingAnswerError
  }

  /** Returns an HTML list containing all of the HTML string elements as items in the list. */
  private fun convertItemsToAnswer(htmlItems: List<StringList>): ListOfSetsOfHtmlStrings {
    Log.d("draganddrop", "convertItemsToAnswer: html Items - $htmlItems")
    return ListOfSetsOfHtmlStrings.newBuilder()
      .addAllSetOfHtmlStrings(htmlItems)
      .build()
  }

  /** Returns whether the grouping is allowed or not for [DragAndDropSortInteractionViewModel]. */
  fun getGroupingStatus(): Boolean {
    return allowMultipleItemsInSamePosition
  }

/*  fun subscribeToCurrentState() {
    ephemeralStateLiveData.observe(
      fragment,
      {
        result ->
        processEphemeralStateResult(result)
      }
    )
  }*/

  /*fun processEphemeralStateResult(result: AsyncResult<EphemeralState>) {
    when (result) {
      is AsyncResult.Failure -> null
      is AsyncResult.Pending -> {} // Display nothing until a valid result is available.
      is AsyncResult.Success -> checkHasPreviousState(result.value)
    }
  }

  fun checkHasPreviousState(ephemeralState: EphemeralState) {
    Log.d("haspreviousstate", "checkHasPreviousState: Drag and Drop has ephemeral State? - ${ephemeralState.pendingState.wrongAnswerList}")
    Log.d("haspreviousstate", "checkHasPreviousState: Drag and Drop has ephemeral State? - ${ephemeralState.pendingState.wrongAnswerList[0].userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList}")
    Log.d("haspreviousstate", "checkHasPreviousState: Drag and Drop has ephemeral State? - ${ephemeralState.pendingState.wrongAnswerList[0].userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList[0].contentIdsList[0].contentId}")
  }*/

  fun updateList(
    itemIndex: Int,
    adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
  ) {
    Log.d("draganddrop", "updateList: Updating the list")
    val item = _choiceItems[itemIndex]
    val nextItem = _choiceItems[itemIndex + 1]
    nextItem.htmlContent = SetOfTranslatableHtmlContentIds.newBuilder().apply {
      addAllContentIds(nextItem.htmlContent.contentIdsList)
      addAllContentIds(item.htmlContent.contentIdsList)
    }.build()
    _choiceItems[itemIndex + 1] = nextItem

    _choiceItems.removeAt(itemIndex)

    _choiceItems.forEachIndexed { index, dragDropInteractionContentViewModel ->
      dragDropInteractionContentViewModel.itemIndex = index
      dragDropInteractionContentViewModel.listSize = _choiceItems.size
    }
    // to update the content of grouped item
    (adapter as BindableAdapter<*>).setDataUnchecked(_choiceItems)
  }

  fun unlinkElement(itemIndex: Int, adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
    val item = _choiceItems[itemIndex]
    _choiceItems.removeAt(itemIndex)
    item.htmlContent.contentIdsList.forEach { contentId ->
      Log.d("haspreviousstate", "computeSelectedChoiceItems: Calling from unlink element")
      val contentIdHtmlMap4: Map<String, String> =
        mapOf(Pair("ca_choices_0", "hi4"), Pair("ca_choices_1", "bye4"), Pair("ca_choices_2", "chao4"), Pair("ca_choices_3", "tata4"))
      _choiceItems.add(
        itemIndex,
        DragDropInteractionContentViewModel(
          contentIdHtmlMap = contentIdHtmlMap,
          htmlContent = SetOfTranslatableHtmlContentIds.newBuilder().apply {
            addContentIds(contentId)
          }.build(),
          itemIndex = 0,
          listSize = 0,
          dragAndDropSortInteractionViewModel = this,
          resourceHandler = resourceHandler,
          null,
          null
        )
      )
    }

    _choiceItems.forEachIndexed { index, dragDropInteractionContentViewModel ->
      dragDropInteractionContentViewModel.itemIndex = index
      dragDropInteractionContentViewModel.listSize = _choiceItems.size
    }
    // to update the list
    (adapter as BindableAdapter<*>).setDataUnchecked(_choiceItems)
  }

  private fun getSubmitTimeError(): DragAndDropSortInteractionError {
    return if (_originalChoiceItems == _choiceItems) {
      DragAndDropSortInteractionError.EMPTY_INPUT
    } else
      DragAndDropSortInteractionError.VALID
  }

  /** Implementation of [StateItemViewModel.InteractionItemFactory] for this view model. */
  class FactoryImpl @Inject constructor(
    private val resourceHandler: AppLanguageResourceHandler,
    private val translationController: TranslationController,
    private val fragment: Fragment,
    private val explorationProgressController: ExplorationProgressController
  ) : InteractionItemFactory {
    override fun create(
      entityId: String,
      hasConversationView: Boolean,
      interaction: Interaction,
      interactionAnswerReceiver: InteractionAnswerReceiver,
      answerErrorReceiver: InteractionAnswerErrorOrAvailabilityCheckReceiver,
      hasPreviousButton: Boolean,
      isSplitView: Boolean,
      writtenTranslationContext: WrittenTranslationContext,
      timeToStartNoticeAnimationMs: Long?,
      userAnswerState: UserAnswerState
    ): StateItemViewModel {
      return DragAndDropSortInteractionViewModel(
        entityId,
        hasConversationView,
        interaction,
        answerErrorReceiver,
        isSplitView,
        writtenTranslationContext,
        resourceHandler,
        translationController,
        userAnswerState,
        fragment,
        explorationProgressController
      )
    }
  }

  override fun getUserAnswerState(): UserAnswerState {
    if (_choiceItems == _originalChoiceItems) {
      return UserAnswerState.newBuilder().apply {
        this.answerErrorCategory = answerErrorCetegory
      }.build()
    }
    return UserAnswerState.newBuilder().apply {
      val htmlContentIds = _choiceItems.map { it.htmlContent }
      listOfSetsOfTranslatableHtmlContentIds =
        ListOfSetsOfTranslatableHtmlContentIds.newBuilder().apply {
          addAllContentIdLists(htmlContentIds)
        }.build()
      answerErrorCategory = answerErrorCetegory
    }.build()
  }




  // resets
  /*companion object {
    private fun computeOriginalChoiceItems(
      contentIdHtmlMap: Map<String, String>,
      choiceStrings: List<SubtitledHtml>,
      dragAndDropSortInteractionViewModel: DragAndDropSortInteractionViewModel,
      resourceHandler: AppLanguageResourceHandler,
      fragment: Fragment,
      explorationProgressController: ExplorationProgressController
    ): MutableList<DragDropInteractionContentViewModel> {

      val ephemeralStateLiveData: LiveData<AsyncResult<EphemeralState>> by lazy {
        explorationProgressController.getCurrentState().toLiveData()
      }

      fun processEphemeralStateResult(result: AsyncResult<EphemeralState>): MutableList<DragDropInteractionContentViewModel> {
        when (result) {
          is AsyncResult.Failure -> {}
          is AsyncResult.Pending -> {} // Display nothing until a valid result is available.
          is AsyncResult.Success -> {

            return choiceStrings.mapIndexed { index, subtitledHtml ->
              Log.d("haspreviousstate", "computeSelectedChoiceItems: Calling from compute original choice items")
              DragDropInteractionContentViewModel(
                contentIdHtmlMap = contentIdHtmlMap,
                htmlContent = SetOfTranslatableHtmlContentIds.newBuilder().apply {
                  addContentIds(
                    TranslatableHtmlContentId.newBuilder().apply {
                      Log.d("contentid", "computeOriginalChoiceItems: content id is - $contentId")
//                contentId = subtitledHtml.contentId
//                contentId = ephemeralState.pendingState.wrongAnswerList[0].userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList[0].contentIdsList[0].contentId
                Log.d("haspreviousstate", "computeOriginalChoiceItems: ephState? - ${result.value}")
                Log.d("haspreviousstate", "computeOriginalChoiceItems: ephState index value - ${result.value.pendingState.wrongAnswerList.get(0).userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList[index].contentIdsList[0].contentId}")
//                Log.d("haspreviousstate", "computeOriginalChoiceItems: ephState - ${ephState?.pendingState?.wrongAnswerList?.get(0)?.userAnswer?.answer?.listOfSetsOfTranslatableHtmlContentIds?.contentIdListsList?.get(0)?.contentIdsList?.get(0)?.contentId}")
                      contentId = result.value.pendingState.wrongAnswerList[0].userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList[index].contentIdsList[0].contentId
                        ?: subtitledHtml.contentId
                    }
                  )
                }.build(),
                itemIndex = index,
                listSize = choiceStrings.size,
                dragAndDropSortInteractionViewModel = dragAndDropSortInteractionViewModel,
                resourceHandler = resourceHandler,
                null,
                null
              )
            }.toMutableList()

          }
        }

        return choiceStrings.mapIndexed { index, subtitledHtml ->
          Log.d("haspreviousstate", "computeSelectedChoiceItems: Calling from compute original choice items")
          DragDropInteractionContentViewModel(
            contentIdHtmlMap = contentIdHtmlMap,
            htmlContent = SetOfTranslatableHtmlContentIds.newBuilder().apply {
              addContentIds(
                TranslatableHtmlContentId.newBuilder().apply {
                  Log.d("contentid", "computeOriginalChoiceItems: content id is - $contentId")
                  contentId = subtitledHtml.contentId
//                contentId = ephemeralState.pendingState.wrongAnswerList[0].userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList[0].contentIdsList[0].contentId
//                Log.d("haspreviousstate", "computeOriginalChoiceItems: ephState? - ${ephState}")
//                Log.d("haspreviousstate", "computeOriginalChoiceItems: ephState - ${ephState?.pendingState?.wrongAnswerList?.get(0)?.userAnswer?.answer?.listOfSetsOfTranslatableHtmlContentIds?.contentIdListsList?.get(0)?.contentIdsList?.get(0)?.contentId}")
                  *//*contentId = ephState?.pendingState?.wrongAnswerList?.get(0)?.userAnswer?.answer?.listOfSetsOfTranslatableHtmlContentIds?.contentIdListsList?.get(index)?.contentIdsList?.get(0)?.contentId
                    ?: subtitledHtml.contentId*//*
                }
              )
            }.build(),
            itemIndex = index,
            listSize = choiceStrings.size,
            dragAndDropSortInteractionViewModel = dragAndDropSortInteractionViewModel,
            resourceHandler = resourceHandler,
            null,
            null
          )
        }.toMutableList()
      }

//      fun subscribeToCurrentState() {
        ephemeralStateLiveData.observe(
          fragment
        ) { result ->
          processEphemeralStateResult(result)
        }
//      }

      *//*fun processEphemeralStateResult(result: AsyncResult<EphemeralState>) {
        when (result) {
          is AsyncResult.Failure -> null
          is AsyncResult.Pending -> {} // Display nothing until a valid result is available.
          is AsyncResult.Success -> checkHasPreviousState(result.value)
        }
      }*//*


//      Log.d("haspreviousstate", "computeSelectedChoiceItems: from compute original choice - $sub")
      Log.d("haspreviousstate", "computeSelectedChoiceItems: from compute original choice - $choiceStrings")
      return choiceStrings.mapIndexed { index, subtitledHtml ->
        Log.d("haspreviousstate", "computeSelectedChoiceItems: Calling from compute original choice items")
        DragDropInteractionContentViewModel(
          contentIdHtmlMap = contentIdHtmlMap,
          htmlContent = SetOfTranslatableHtmlContentIds.newBuilder().apply {
            addContentIds(
              TranslatableHtmlContentId.newBuilder().apply {
                Log.d("contentid", "computeOriginalChoiceItems: content id is - $contentId")
                contentId = subtitledHtml.contentId
//                contentId = ephemeralState.pendingState.wrongAnswerList[0].userAnswer.answer.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList[0].contentIdsList[0].contentId
//                Log.d("haspreviousstate", "computeOriginalChoiceItems: ephState? - ${ephState}")
//                Log.d("haspreviousstate", "computeOriginalChoiceItems: ephState - ${ephState?.pendingState?.wrongAnswerList?.get(0)?.userAnswer?.answer?.listOfSetsOfTranslatableHtmlContentIds?.contentIdListsList?.get(0)?.contentIdsList?.get(0)?.contentId}")
                *//*contentId = ephState?.pendingState?.wrongAnswerList?.get(0)?.userAnswer?.answer?.listOfSetsOfTranslatableHtmlContentIds?.contentIdListsList?.get(index)?.contentIdsList?.get(0)?.contentId
                  ?: subtitledHtml.contentId*//*
              }
            )
          }.build(),
          itemIndex = index,
          listSize = choiceStrings.size,
          dragAndDropSortInteractionViewModel = dragAndDropSortInteractionViewModel,
          resourceHandler = resourceHandler,
          null,
          null
        )
      }.toMutableList()
    }
  }*/








  companion object {
    private fun computeOriginalChoiceItems(
      contentIdHtmlMap: Map<String, String>,
      choiceStrings: List<SubtitledHtml>,
      dragAndDropSortInteractionViewModel: DragAndDropSortInteractionViewModel,
      resourceHandler: AppLanguageResourceHandler,
      fragment: Fragment,
      explorationProgressController: ExplorationProgressController
    ): MutableList<DragDropInteractionContentViewModel> {

      var vm : DragDropInteractionContentViewModel

      var updatedContentIdMap: Map<String?, String?> = mapOf()

      val ephemeralStateLiveData: LiveData<AsyncResult<EphemeralState>> by lazy {
        explorationProgressController.getCurrentState().toLiveData()
      }

      var choiceItems: MutableList<DragDropInteractionContentViewModel> = mutableListOf()

      fun processEphemeralStateResult(result: AsyncResult<EphemeralState>){
        choiceItems = when (result) {
//          is AsyncResult.Failure -> mutableListOf()
//          is AsyncResult.Pending -> mutableListOf()
          is AsyncResult.Success -> {
            val state = result.value
            val wrongAnswerList = state.pendingState.wrongAnswerList

            Log.d("haspreviousstate", "computeOriginalChoiceItems: wrong answer list - $wrongAnswerList")

            choiceStrings.mapIndexed { index, subtitledHtml ->
              Log.d("haspreviousstate", "computeOriginalChoiceItems: Computing original choice items")

              val contentIdFromWrongAnswer = wrongAnswerList?.lastOrNull()
//                ?.firstOrNull()
                ?.userAnswer
                ?.answer
                ?.listOfSetsOfTranslatableHtmlContentIds
                ?.contentIdListsList
                ?.getOrNull(index)
                ?.contentIdsList
                ?.firstOrNull()
                ?.contentId
//              Log.d("haspreviousstate", "computeOriginalChoiceItems: Wrong answer list - ${wrongAnswerList?.lastOrNull()?.userAnswer?.answer?.listOfSetsOfTranslatableHtmlContentIds?.contentIdListsList[0]?.contentIdsList[0]?.contentId}")

              val contentHtmlFromWrongAnswer = wrongAnswerList?.lastOrNull()
                ?.userAnswer
                ?.listOfHtmlAnswers
                ?.setOfHtmlStringsList
                ?.get(index)
                ?.htmlList
                ?.firstOrNull()

              Log.d("contenthtmlfromwrong", "processEphemeralStateResult: 2 Content id - $contentIdFromWrongAnswer")
              Log.d("contenthtmlfromwrong", "processEphemeralStateResult: 2 Content html - $contentHtmlFromWrongAnswer")

//              updatedContentIdMap.plus(Pair(contentIdFromWrongAnswer, contentHtmlFromWrongAnswer))
              updatedContentIdMap = updatedContentIdMap.plus(Pair(contentIdFromWrongAnswer, contentHtmlFromWrongAnswer))


              Log.d("contenthtmlfromwrong", "processEphemeralStateResult: updated Content id map - $updatedContentIdMap")

              val contentIdHtmlMap1: Map<String, String> =
              mapOf(Pair("ca_choices_0", "hi1"), Pair("ca_choices_1", "bye1"), Pair("ca_choices_2", "chao1"), Pair("ca_choices_3", "tata1"))


              vm = DragDropInteractionContentViewModel(
//                contentIdHtmlMap = (updatedContentIdMap ?: contentIdHtmlMap) as Map<String, String>,
                contentIdHtmlMap = (updatedContentIdMap) as Map<String, String>,
//                contentIdHtmlMap = contentIdHtmlMap1,
                htmlContent = SetOfTranslatableHtmlContentIds.newBuilder().apply {
                  addContentIds(
                    TranslatableHtmlContentId.newBuilder().apply {
                      contentId = contentIdFromWrongAnswer ?: subtitledHtml.contentId
                      Log.d("contentid", "computeOriginalChoiceItems: content id is - $contentId")
                    }
                  )
                }.build(),
                itemIndex = index,
                listSize = choiceStrings.size,
                dragAndDropSortInteractionViewModel = dragAndDropSortInteractionViewModel,
                resourceHandler = resourceHandler,
                null,
                null
              )

              vm.computeStringList()

              /*DragDropInteractionContentViewModel(
//                contentIdHtmlMap = (updatedContentIdMap ?: contentIdHtmlMap) as Map<String, String>,
                contentIdHtmlMap = (updatedContentIdMap) as Map<String, String>,
//                contentIdHtmlMap = contentIdHtmlMap1,
                htmlContent = SetOfTranslatableHtmlContentIds.newBuilder().apply {
                  addContentIds(
                    TranslatableHtmlContentId.newBuilder().apply {
                      contentId = contentIdFromWrongAnswer ?: subtitledHtml.contentId
                      Log.d("contentid", "computeOriginalChoiceItems: content id is - $contentId")
                    }
                  )
                }.build(),
                itemIndex = index,
                listSize = choiceStrings.size,
                dragAndDropSortInteractionViewModel = dragAndDropSortInteractionViewModel,
                resourceHandler = resourceHandler,
                null,
                null
              )*/
              vm
            }.toMutableList()
          }
          else -> mutableListOf()
        }
      }

      val deferredResult = CompletableDeferred<AsyncResult<EphemeralState>>()

      fun observeEphemeralState() {
        ephemeralStateLiveData.observe(fragment) { result ->
          deferredResult.complete(result)
        }
      }

      suspend fun getEphemeralStateResult(): AsyncResult<EphemeralState> {
        observeEphemeralState()
        return deferredResult.await()
      }

// Usage in a coroutine
      GlobalScope.launch(Dispatchers.Main) {
        val result = getEphemeralStateResult()
        Log.d("contentid", "computeOriginalChoiceItems: in launch - $result")

        // Process the result here
      }


      runBlocking {
      ephemeralStateLiveData.observe(fragment) { result ->
        Log.d("contentid", "computeOriginalChoiceItems: in ephemeral state live data - $choiceItems")
        Log.d("contentid", "computeOriginalChoiceItems: content id map - $contentIdHtmlMap")
          processEphemeralStateResult(result)
        }
        Log.d("contentid", "computeOriginalChoiceItems: in ephemeral state live data 2 - $choiceItems")
      }


      Log.d("contentid", "computeOriginalChoiceItems: returning - $choiceItems")
//      return choiceItems

      return choiceItems.ifEmpty {
        Log.d("contentid", "computeOriginalChoiceItems: empty choice items - $choiceItems")
        val contentIdHtmlMap3: Map<String, String> =
        mapOf(Pair("ca_choices_0", "hi3"), Pair("ca_choices_1", "bye3"), Pair("ca_choices_2", "chao3"), Pair("ca_choices_3", "tata3"))
        choiceStrings.mapIndexed { index, subtitledHtml ->
          DragDropInteractionContentViewModel(
            contentIdHtmlMap = contentIdHtmlMap3,
//            contentIdHtmlMap = (updatedContentIdMap) as Map<String, String> ?: contentIdHtmlMap3,
            htmlContent = SetOfTranslatableHtmlContentIds.newBuilder().apply {
              addContentIds(
                TranslatableHtmlContentId.newBuilder().apply {
                  contentId = subtitledHtml.contentId
                }
              )
            }.build(),
            itemIndex = index,
            listSize = choiceStrings.size,
            dragAndDropSortInteractionViewModel = dragAndDropSortInteractionViewModel,
            resourceHandler = resourceHandler,
            null,
            null
          )
        }.toMutableList()
      }
    }
  }







  private fun computeSelectedChoiceItems(
    contentIdHtmlMap: Map<String, String>,
    choiceStrings: List<SubtitledHtml>,
    dragAndDropSortInteractionViewModel: DragAndDropSortInteractionViewModel,
    resourceHandler: AppLanguageResourceHandler,
    userAnswerState: UserAnswerState
  ): MutableList<DragDropInteractionContentViewModel> {
    return if (userAnswerState.listOfSetsOfTranslatableHtmlContentIds.contentIdListsCount == 0) {
        Log.d("contentid", "computeOriginalChoiceItems: here in if")
//        Log.d("contentid", "computeOriginalChoiceItems: here in if with sub - $sub")
      _originalChoiceItems.toMutableList()
    } else {
      Log.d("haspreviousstate", "computeSelectedChoiceItems: Calling from compute selected choice items")
      userAnswerState.listOfSetsOfTranslatableHtmlContentIds.contentIdListsList
        .mapIndexed { index, contentId ->
          Log.d("contentid", "computeOriginalChoiceItems: content id from computeselected is - $contentId")
          val contentIdHtmlMap2: Map<String, String> =
            mapOf(Pair("ca_choices_0", "hi2"), Pair("ca_choices_1", "bye2"), Pair("ca_choices_2", "chao2"), Pair("ca_choices_3", "tata2"))
          DragDropInteractionContentViewModel(
            contentIdHtmlMap = contentIdHtmlMap2,
            htmlContent = contentId,
            itemIndex = index,
            listSize = choiceStrings.size,
            dragAndDropSortInteractionViewModel = dragAndDropSortInteractionViewModel,
            resourceHandler = resourceHandler,
            null,
            null
          )
        }.toMutableList()
    }
  }
}
