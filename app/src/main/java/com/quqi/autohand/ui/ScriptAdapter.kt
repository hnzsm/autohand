package com.quqi.autohand.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.quqi.autohand.databinding.ItemScriptBinding
import com.quqi.autohand.model.Script

class ScriptAdapter(
    private var scripts: List<Script>,
    private var selectedId: String?,
    private val onSelect: (Script) -> Unit,
    private val onRename: (Script) -> Unit,
    private val onDelete: (Script) -> Unit
) : RecyclerView.Adapter<ScriptAdapter.Holder>() {

    fun submit(list: List<Script>, selectedId: String?) {
        scripts = list
        this.selectedId = selectedId
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemScriptBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemScriptBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = scripts.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val script = scripts[position]
        val selected = script.id == selectedId
        with(holder.binding) {
            scriptName.text = script.name
            scriptSummary.text = script.summary()
            scriptFlag.text = if (selected) "使用中" else ""
            scriptRow.alpha = if (selected) 1f else 0.7f
            scriptRow.setOnClickListener { onSelect(script) }
            btnRename.setOnClickListener { onRename(script) }
            btnDelete.setOnClickListener { onDelete(script) }
        }
    }
}
