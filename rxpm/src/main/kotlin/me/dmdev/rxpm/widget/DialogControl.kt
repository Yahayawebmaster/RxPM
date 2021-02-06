/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2021 Dmitriy Gorbunov (dmitriy.goto@gmail.com)
 *                     and Vasili Chyrvon (vasili.chyrvon@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.dmdev.rxpm.widget

import android.app.Dialog
import android.app.DialogFragment
import io.reactivex.Maybe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import me.dmdev.rxpm.AndroidPmView
import me.dmdev.rxpm.PresentationModel
import me.dmdev.rxpm.widget.DialogControl.Display.Absent
import me.dmdev.rxpm.widget.DialogControl.Display.Displayed

/**
 *
 * Helps to display a dialog and get the result in a reactive form.
 * Takes care of all lifecycle processing.
 *
 * The dialog attached using [AndroidPmView.bindTo] will be
 * automatically dismissed and restored on config changes ([UNBINDED][PresentationModel.Lifecycle.UNBINDED]
 * and [BINDED][PresentationModel.Lifecycle.BINDED] states correspondingly).
 * So there is no need to use [DialogFragment] or something similar.
 *
 * You can bind this to any subclass of [Dialog] using the familiar `bindTo` methods
 * in the [AndroidPmView].
 *
 * Instantiate this using the [dialogControl] extension function of the presentation model.
 *
 * @param T the type of the data to display in the dialog.
 * @param R the type of the result we get from the dialog.
 *
 * @see InputControl
 * @see CheckControl
 *
 * @since 1.2
 */
class DialogControl<T, R> internal constructor(pm: PresentationModel) {

    val displayed = pm.State<Display>(Absent)
    private val result = pm.Action<R>()

    /**
     * Shows the dialog.
     *
     * @param data the data to display in the dialog.
     */
    fun show(data: T) {
        dismiss()
        displayed.relay.accept(Displayed(data))
    }

    /**
     * Shows the dialog and waits for the result.
     *
     * @param data the data to display in the dialog.
     * @return [Maybe] that waits for the result of the dialog.
     */
    fun showForResult(data: T): Maybe<R> {

        dismiss()

        return result.relay
            .doOnSubscribe {
                displayed.relay.accept(Displayed(data))
            }
            .takeUntil(
                displayed.relay
                    .skip(1)
                    .filter { it == Absent }
            )
            .firstElement()
    }

    /**
     * Sends the [result] of the dialog and then dismisses the dialog.
     */
    fun sendResult(result: R) {
        this.result.consumer.accept(result)
        dismiss()
    }

    /**
     * Dismisses the dialog associated with this [DialogControl].
     */
    fun dismiss() {
        if (displayed.valueOrNull is Displayed<*>) {
            displayed.relay.accept(Absent)
        }
    }

    sealed class Display {
        data class Displayed<T>(val data: T) : Display()
        object Absent : Display()
    }
}

/**
 * Creates the [DialogControl].
 *
 * @param T the type of the data to display in the dialog.
 * @param R the type of the result we get from the dialog.
 *
 * @since 1.2
 */
fun <T, R> PresentationModel.dialogControl(): DialogControl<T, R> {
    return DialogControl(this)
}

internal inline fun <T, R> DialogControl<T, R>.bind(
    crossinline createDialog: (data: T, dc: DialogControl<T, R>) -> Dialog,
    compositeDisposable: CompositeDisposable
) {

    var dialog: Dialog? = null

    val closeDialog: () -> Unit = {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    compositeDisposable.add(
        displayed.observable
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally { closeDialog() }
            .subscribe {
                @Suppress("UNCHECKED_CAST")
                if (it is Displayed<*>) {
                    dialog = createDialog(it.data as T, this)
                    dialog?.setOnDismissListener { this.dismiss() }
                    dialog?.show()
                } else if (it === Absent) {
                    closeDialog()
                }
            }
    )
}

