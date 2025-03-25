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
    private DefaultTableModel tableModel = null;
    private JScrollPane memoryTableScrollPane = null;

    private static final int BYTES_IN_MEMORY = 0x10_000;
    private static final int BYTES_PER_ROW = 16;
    private static final int ADDRESS_COL_WIDTH = 60;
    private static final int BYTE_COL_WIDTH = 40;
    private static final int FONT_SIZE = 12;

    // the memory table is the biggest element in the debugger, and we use it to find a reasonable size for the frame, such that
    // the rest of the components can simply take up maximum space
    private static final int MEMORY_TABLE_WIDTH = 720;
    /** Relative to screen height! */
    private static final double MEMORY_TABLE_RELATIVE_HEIGHT = 0.4;

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
        for (int i = 0; i < BYTES_IN_MEMORY; i += BYTES_PER_ROW) {
            String address = String.format("$%04X", i);
            Object [] row = new Object [columnNames.length];
            row[0] = address;
            for (int j = 0; j < BYTES_PER_ROW; j++) {
                row[j + 1] = String.format("%02X", memory.unconditionalRead(i + j));
            }
            tableModel.addRow(row);
        }

        // Create Table
	JTable memoryTable = new JTable(tableModel);
        // remove default keybinding, our table isn't editable anyway!
        memoryTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F2"), "none");

        // reasonable default font size
        memoryTable.setFont(new Font("Monospaced", Font.PLAIN, FONT_SIZE));
        memoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableColumnModel colMod = memoryTable.getColumnModel();
        colMod.getColumn(0).setResizable(false);
        colMod.getColumn(0).setPreferredWidth(ADDRESS_COL_WIDTH);
        for (int i = 1; i < (BYTES_PER_ROW + 1); i++) {
            colMod.getColumn(i).setResizable(false);
            colMod.getColumn(i).setPreferredWidth(BYTE_COL_WIDTH);
        }

        memoryTableScrollPane = new JScrollPane(memoryTable);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        memoryTableScrollPane.setPreferredSize(new Dimension(MEMORY_TABLE_WIDTH, (int)(screenSize.height * MEMORY_TABLE_RELATIVE_HEIGHT)));
    }

    /**
     * To fulfill interface contract. Updates a value at given index.
     * @param index
     */
    public void memoryChanged(int index) {
        tableModel.setValueAt(String.format("%02X", memory.unconditionalRead(index)), index / BYTES_PER_ROW, (index % BYTES_PER_ROW) + 1);
        // memoryTable.clearSelection();
        // memoryTable.changeSelection(index / 16, (index % 16) + 1, false, false);
    }

    /**
     * The timers update very often! This method is used to update the timers only once per frame, instead of every time they update.
     */
    public void updateTimersInDebugger() {
        // FF04 == div, FF05 = tima
        tableModel.setValueAt(String.format("%02X", memory.unconditionalRead(0xFF04)), 0xFF04 / BYTES_PER_ROW, (0xFF04 % BYTES_PER_ROW) + 1);
        tableModel.setValueAt(String.format("%02X", memory.unconditionalRead(0xFF05)), 0xFF05 / BYTES_PER_ROW, (0xFF05 % BYTES_PER_ROW) + 1);
    }

    /**
     * Getter for the JScrollPane.
     * @return JScrollPane to add to debugging JFrame.
     */
    public JScrollPane getMemoryTableScrollPane() {
        return memoryTableScrollPane;
    }
}
