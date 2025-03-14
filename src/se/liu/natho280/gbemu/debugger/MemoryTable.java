package se.liu.natho280.gbemu.debugger;

import se.liu.natho280.gbemu.cpu.Memory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;

/**
 * Creates a table displaying the various changes made to the memory in real time, for showing in the main debugging
 * JFrame. Once constructed, fetch the JScrollPane with getMemoryTableScrollPane and add it to the main JFrame.
 */
public class MemoryTable implements MemoryListener {
    private Memory memory;
    private JTable memoryTable = null;
    private DefaultTableModel tableModel = null;
    private JScrollPane memoryTableScrollPane = null;

    public MemoryTable(Memory memory) {
        this.memory = memory;
        memory.addMemoryListener(this);

        makeScrollPane();
    }

    /**
     * Helper function for constructor.
     */
    private void makeScrollPane() {
        String[] columnNames = new String[17];
        columnNames[0] = "Address";
        for (int i = 1; i < 17; i++) {
            columnNames[i] = "$" + Integer.toHexString(i - 1).toUpperCase();
        }

        this.tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // We want to populate the addresses from $0000 to $FFFF
        for (int i = 0; i < 0x10_000; i += 0x10) {
            String address = String.format("$%04X", i);
            Object [] row = new Object [columnNames.length];
            row[0] = address;
            for (int j = 0; j < 16; j++) {
                row[j + 1] = String.format("%02X", memory.unconditionalRead(i + j));
            }
            tableModel.addRow(row);
        }

        // Create Table
        this.memoryTable = new JTable(tableModel);
        // remove default keybinding, our table isn't editable anyway!
        this.memoryTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F2"), "none");

        this.memoryTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        memoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableColumnModel colMod = memoryTable.getColumnModel();
        colMod.getColumn(0).setResizable(false);
        colMod.getColumn(0).setPreferredWidth(60);
        for (int i = 1; i < 17; i++) {
            colMod.getColumn(i).setResizable(false);
            colMod.getColumn(i).setPreferredWidth(40);
        }

        memoryTableScrollPane = new JScrollPane(memoryTable);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        memoryTableScrollPane.setPreferredSize(new Dimension(720, (int)(screenSize.height * 0.4)));
    }

    /**
     * To fulfill interface contract. Updates a value at given index.
     * @param index
     */
    public void memoryChanged(int index) {
        tableModel.setValueAt(String.format("%02X", memory.unconditionalRead(index)), index / 16, (index % 16) + 1);
        // memoryTable.clearSelection();
        // memoryTable.changeSelection(index / 16, (index % 16) + 1, false, false);
    }

    /**
     * Getter for the JScrollPane.
     * @return JScrollPane to add to debugging JFrame.
     */
    public JScrollPane getMemoryTableScrollPane() {
        return memoryTableScrollPane;
    }
}
