package com.example.disastermesh;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.bleprototype.R;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<EmergencyReport> reportList;

    public ReportAdapter(List<EmergencyReport> reportList) {
        this.reportList = reportList;
    }

    public void setReports(List<EmergencyReport> reports) {
        this.reportList = reports;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        EmergencyReport report = reportList.get(position);
        holder.tvType.setText(report.getType() + " [" + report.getSeverity() + "]");
        holder.tvSender.setText("From: " + report.getSourceDevice() + " | TTL: " + report.getTtl());
        holder.tvDescription.setText("ID: " + report.getPacketId().substring(0, Math.min(8, report.getPacketId().length())) + "...");
        holder.tvLocation.setText("Location: " + report.getLocation());
        holder.tvStatus.setText("Status: " + report.getStatus() + " (Relays: " + report.getRelayCount() + ")");
    }

    @Override
    public int getItemCount() {
        return reportList != null ? reportList.size() : 0;
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvSender, tvDescription, tvLocation, tvStatus;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvType);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
