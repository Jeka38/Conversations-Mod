package eu.siacs.conversations.ui.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import eu.siacs.conversations.entities.Presence
import eu.siacs.conversations.ui.util.StyledAttributes
import eu.siacs.conversations.utils.UIHelper

class PresenceIndicator : View {
    private var paint: Paint = Paint().also {
        it.setColor(StyledAttributes.getColor(this.context, androidx.appcompat.R.attr.colorPrimaryDark))
        it.style = Paint.Style.STROKE
        it.strokeWidth = 1 * Resources.getSystem().displayMetrics.density
    }

    var status: Presence.Status? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val color: Int? = UIHelper.getColorForStatus(status);

        if (color != null) {
            canvas.drawColor(color)
            canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)
        }
    }
}