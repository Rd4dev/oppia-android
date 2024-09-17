package org.oppia.android.app.player.state.itemviewmodel

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import org.oppia.android.R
import org.oppia.android.app.model.EphemeralState
import org.oppia.android.app.model.SetOfTranslatableHtmlContentIds
import org.oppia.android.app.model.StringList
import org.oppia.android.app.translation.AppLanguageResourceHandler
import org.oppia.android.app.viewmodel.ObservableViewModel
import org.oppia.android.domain.exploration.ExplorationProgressController
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProviders.Companion.toLiveData

/** [ObservableViewModel] for DragDropSortInput values. */
class DragDropInteractionContentViewModel(
  private val contentIdHtmlMap: Map<String, String>,
  var htmlContent: SetOfTranslatableHtmlContentIds,
  var itemIndex: Int,
  var listSize: Int,
  val dragAndDropSortInteractionViewModel: DragAndDropSortInteractionViewModel,
  private val resourceHandler: AppLanguageResourceHandler,
  private val fragment: Fragment?,
  private val explorationProgressController: ExplorationProgressController?
) : ObservableViewModel() {

  /*private val ephemeralStateLiveData: LiveData<AsyncResult<EphemeralState>>? by lazy {
    explorationProgressController?.getCurrentState()?.toLiveData()
  }

  fun ss() {
    ephemeralStateLiveData?.observe(
      fragment ?: return
    ) { result ->
      processEphemeralStateResult(result)
    }
  }*/

  private val ephemeralStateLiveData: LiveData<AsyncResult<EphemeralState>>? by lazy {
    explorationProgressController?.getCurrentState()?.toLiveData()
  }

  fun ss() {
    Log.d("haspreviousstate", "ss: called ss")
    ephemeralStateLiveData?.observe(
      fragment ?: return
    ) { result ->
      processEphemeralStateResult(result)
    } ?: run {}
  }

  private fun processEphemeralStateResult(result: AsyncResult<EphemeralState>) {
    Log.d("haspreviousstate", "ss: called process ephemeral state result")
    when (result) {
      is AsyncResult.Failure -> null
      is AsyncResult.Pending -> {} // Display nothing until a valid result is available.
      is AsyncResult.Success -> checkHasPreviousState(result.value)
    }
  }

  private fun checkHasPreviousState(ephemeralState: EphemeralState) {
    Log.d("haspreviousstate", "checkHasPreviousState: Has ephemeral State? - ${ephemeralState.hasPendingState()}")
  }

  fun handleGrouping(adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
    dragAndDropSortInteractionViewModel.updateList(itemIndex, adapter)
  }

  fun handleUnlinking(adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
    dragAndDropSortInteractionViewModel.unlinkElement(itemIndex, adapter)
  }

  fun handleUpMovement(adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
    dragAndDropSortInteractionViewModel.onItemMoved(itemIndex, itemIndex - 1, adapter)
  }

  fun handleDownMovement(adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
    dragAndDropSortInteractionViewModel.onItemMoved(itemIndex, itemIndex + 1, adapter)
  }

  /**
   * Returns a [StringList] corresponding to a list of HTML strings that can be displayed to the
   * user.
   */
  fun computeStringList(): StringList = StringList.newBuilder().apply {
    Log.d("draganddrop", "computeStringList: Computing string list")
    Log.d("draganddrop", "computeStringList: Computing string list html content - $htmlContent")
    // Computing string list html map - {ca_choices_0=hi3, ca_choices_1=bye3, ca_choices_2=chao3, ca_choices_3=tata3}
    // Displays as 0, 1, 2, 3
    Log.d("draganddrop", "computeStringList: Computing string list html map - $contentIdHtmlMap")
    Log.d("draganddrop", "computeStringList: Computing string list html map - ${htmlContent.contentIdsList.mapNotNull { contentIdHtmlMap[it.contentId] }}")
    addAllHtml(htmlContent.contentIdsList.mapNotNull { contentIdHtmlMap[it.contentId] })
    ss()
//    addAllHtml(htmlContent.contentIdsList.mapNotNull { "Kye${it.contentId}" })
  }.build()

  fun computeDragDropMoveUpItemContentDescription(): String {
    return if (itemIndex != 0) {
      resourceHandler.getStringInLocaleWithWrapping(
        R.string.move_item_up_content_description, itemIndex.toString()
      )
    } else resourceHandler.getStringInLocale(R.string.up_button_disabled)
  }

  fun computeDragDropMoveDownItemContentDescription(): String {
    return if (itemIndex != listSize - 1) {
      resourceHandler.getStringInLocaleWithWrapping(
        R.string.move_item_down_content_description, (itemIndex + 2).toString()
      )
    } else resourceHandler.getStringInLocale(R.string.down_button_disabled)
  }

  fun computeDragDropGroupItemContentDescription(): String {
    return resourceHandler.getStringInLocaleWithWrapping(
      R.string.link_to_item_below, (itemIndex + 2).toString()
    )
  }

  fun computeDragDropUnlinkItemContentDescription(): String {
    return resourceHandler.getStringInLocaleWithWrapping(
      R.string.unlink_items, (itemIndex + 1).toString()
    )
  }
}
