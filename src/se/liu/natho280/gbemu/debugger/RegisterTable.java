package se.liu.natho280.gbemu.debugger;

import se.liu.natho280.gbemu.cpu.Reg;
import se.liu.natho280.gbemu.cpu.Registers;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Handles the register portion of the debugging GUI. Updates the values by listening
 * on the register writes.
 */
public class RegisterTable implements RegisterListener {
    private Registers registers;
    private DefaultTableModel registerTableModel;
    private JScrollPane registerScrollPane;

    public RegisterTable(Registers registers) {
        this.registers = registers;
        this.registers.addRegisterListener(this);

        makeScrollPane();
    }

    private void makeScrollPane() {

        String[] columnNames = {"Register", "Value"};
        this.registerTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (int i = 0; i < 10; i++) {
            Reg register = matchIndexToRegister(i);
            String[] newRow = new String[2];
            newRow[0] = Reg.toString(register);

            if (register != Reg.SP && register != Reg.PC) {
                newRow[1] = String.format("$%02X", registers.get(register));
            } else {
                // if SP or PC, we want 4 digits
                newRow[1] = String.format("$%04X", registers.get(register));
            }

            this.registerTableModel.addRow(newRow);
        }

	JTable registerTable = new JTable(this.registerTableModel);
        registerTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        this.registerScrollPane = new JScrollPane(registerTable);
    }

    public JScrollPane getRegisterScrollPane() {
        return this.registerScrollPane;
    }

    /**
     * Small helper function that matches an int to a register.
     * @param register
     * @return a register label
     */
    private Reg matchIndexToRegister(int register) {
        return switch (register) {
            case 0 -> Reg.A;
            case 1 -> Reg.F;
            case 2 -> Reg.B;
            case 3 -> Reg.C;
            case 4 -> Reg.D;
            case 5 -> Reg.E;
            case 6 -> Reg.H;
            case 7 -> Reg.L;
            case 8 -> Reg.SP;
            case 9 -> Reg.PC;
            default -> throw new IllegalArgumentException("What?");
        };
    }

    /**
     * Small helper function that matches an int to a register.
     * @param register
     * @return a register label
     */
    private int matchRegisterToIndex(Reg register) {
        return switch (register) {
            case Reg.A -> 0;
            case Reg.F -> 1;
            case Reg.B -> 2;
            case Reg.C -> 3;
            case Reg.D -> 4;
            case Reg.E -> 5;
            case Reg.H -> 6;
            case Reg.L -> 7;
            case Reg.SP -> 8;
            case Reg.PC -> 9;
            default -> throw new IllegalArgumentException("What?");
        };
    }

    public void registerUpdated(Reg reg) {
        if (reg != Reg.SP && reg != Reg.PC) {
            switch (reg) {
                case Reg.AF:
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(Reg.A)),
                            matchRegisterToIndex(Reg.A), 1);
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(Reg.F)),
                            matchRegisterToIndex(Reg.F), 1);
                    break;
                case Reg.BC:
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(Reg.B)),
                            matchRegisterToIndex(Reg.B), 1);
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(Reg.C)),
                            matchRegisterToIndex(Reg.C), 1);
                    break;
                case Reg.DE:
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(Reg.D)),
                            matchRegisterToIndex(Reg.D), 1);
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(Reg.E)),
                            matchRegisterToIndex(Reg.E), 1);
                    break;
                case Reg.HL:
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(Reg.H)),
                            matchRegisterToIndex(Reg.H), 1);
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(Reg.L)),
                            matchRegisterToIndex(Reg.L), 1);
                    break;
                default:
                    this.registerTableModel.setValueAt(String.format("$%02X", registers.get(reg)),
                            matchRegisterToIndex(reg), 1);
                    break;
            }
        } else {
            // if SP or PC, we want 4 digits
            this.registerTableModel.setValueAt(String.format("$%04X", registers.get(reg)),
                    matchRegisterToIndex(reg), 1);
        }
    }

    public int getPC() {
        return registers.get(Reg.PC);
    }
}
