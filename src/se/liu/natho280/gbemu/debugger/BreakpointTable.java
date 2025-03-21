package se.liu.natho280.gbemu.debugger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class BreakpointTable {
    private DefaultTableModel breakpointTableModel = null;
    private JScrollPane breakpointTableScrollPane = null;

    public BreakpointTable() {
	String[] columnNames = {"Breakpoint"};
	this.breakpointTableModel = new DefaultTableModel(columnNames, 0);
	create();
    }

    /**
     * Creates the BreakpointTable, simply a helper function for the constructor
     */
    private void create() {

	JTable breakpointTable = new JTable(this.breakpointTableModel) {
	    @Override public boolean isCellEditable(int row, int column) {
		return false;
	    }
	};

	// remove default keybinding, our table isn't editable anyway!
	breakpointTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F2"), "none");

	breakpointTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
	this.breakpointTableScrollPane = new JScrollPane(breakpointTable);
    }

    /**
     * Retrieves the JScrollPane for displaying in the UI
     * @return
     */
    public JScrollPane getBreakpointScrollPane() {
	return this.breakpointTableScrollPane;
    }

    /**
     * Add a breakpoint to the UI
     * @param address
     */
    public void addBreakpoint(int address) {
	breakpointTableModel.addRow(new String[] { (breakpointTableModel.getRowCount() + 1) + ": $" + Integer.toHexString(address).toUpperCase() });
    }

    /**
     * Remove a given index from the table and recalculate shown indices for each row.
     * @param index index to remove
     */
    public void removeBreakpoint(int index) {
	breakpointTableModel.removeRow(index);

	for (int i = 0; i < this.breakpointTableModel.getRowCount(); i++) {
	    String rowData = (String)breakpointTableModel.getValueAt(i, 0);
	    rowData = rowData.split("\\$")[1];
	    rowData = String.format("%d: $%s", i + 1, rowData);
	    this.breakpointTableModel.setValueAt(rowData, i, 0);
	}
    }

    /**
     * Clears out the breakpoint table of all entries
     */
    public void clear() {
	breakpointTableModel.setRowCount(0);
    }
}
