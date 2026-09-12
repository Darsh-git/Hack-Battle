package com.example.bleprototype;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bleprototype.model.EmergencyPacket;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PacketAdapter extends RecyclerView.Adapter<PacketAdapter.PacketViewHolder> {
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm", Locale.US);

    private List<EmergencyPacket> packets;
    private final OnPacketClickListener listener;

    public interface OnPacketClickListener {
        void onViewReport(EmergencyPacket packet);
    }

    public PacketAdapter(List<EmergencyPacket> packets) {
        this(packets, null);
    }

    public PacketAdapter(List<EmergencyPacket> packets, OnPacketClickListener listener) {
        this.packets = packets;
        this.listener = listener;
    }

    public void setPackets(List<EmergencyPacket> packets) {
        this.packets = packets;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PacketViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report, parent, false);
        return new PacketViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PacketViewHolder holder, int position) {
        EmergencyPacket packet = packets.get(position);
        String severity = packet.getSeverity() == null ? "UNKNOWN" : packet.getSeverity();
        String id = packet.getPacketId();
        holder.type.setText(packet.getType() + "  •  " + severity);
        holder.sender.setText("Packet " + id + "  •  Source: " + packet.getSourceDevice());
        holder.details.setText("TTL: " + packet.getTtl() + "  •  Relays: " + packet.getRelayCount()
                + "  •  Received: " + formatTime(packet.getReceivedAt()));
        holder.location.setText("Location: " + packet.getLocation());
        holder.status.setText("Status: " + packet.getStatus());
        holder.viewReport.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewReport(packet);
            }
        });
        holder.type.setTextColor(severityColor(severity));
        holder.status.setTextColor(statusColor(packet.getStatus()));
    }

    @Override
    public int getItemCount() {
        return packets == null ? 0 : packets.size();
    }

    private static String formatTime(long timestamp) {
        return timestamp <= 0 ? "unknown" : TIME_FORMAT.format(new Date(timestamp));
    }

    private static int severityColor(String severity) {
        if ("CRITICAL".equalsIgnoreCase(severity)) {
            return Color.rgb(183, 28, 28);
        }
        if ("LOW".equalsIgnoreCase(severity)) {
            return Color.rgb(19, 121, 91);
        }
        return Color.rgb(177, 96, 0);
    }

    private static int statusColor(String status) {
        return "PENDING".equalsIgnoreCase(status)
                ? Color.rgb(177, 96, 0) : Color.rgb(19, 121, 91);
    }

    static class PacketViewHolder extends RecyclerView.ViewHolder {
        final TextView type;
        final TextView sender;
        final TextView details;
        final TextView location;
        final TextView status;
        final Button viewReport;

        PacketViewHolder(@NonNull View itemView) {
            super(itemView);
            type = itemView.findViewById(R.id.tvType);
            sender = itemView.findViewById(R.id.tvSender);
            details = itemView.findViewById(R.id.tvDescription);
            location = itemView.findViewById(R.id.tvLocation);
            status = itemView.findViewById(R.id.tvStatus);
            viewReport = itemView.findViewById(R.id.btn_view_report);
        }
    }
}