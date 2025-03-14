package se.liu.natho280.gbemu.debugger;

import se.liu.natho280.gbemu.cpu.Memory;
import se.liu.natho280.gbemu.cpu.Reg;
import se.liu.natho280.gbemu.cpu.Registers;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

// TODO Could add specific line info for cartridge header
// TODO Make this a listener to CPU, highlight current instruction when CPU executes an instruction?

/**
 * Disassembles the memory (and turns it into instructions in string form) for showing in the debugging window.
 * Once constructed, fetch the JScrollPane with getDissassemblyTableScrollPane and add it to the main JFrame.
 */
public class DisassemblyTable {
    private Memory memory;
    private final JTable disassemblyTable;
    private final DefaultTableModel disassemblyTableModel;
    private final JScrollPane disassemblyTableScrollPane;

    public DisassemblyTable(Memory memory) {
        this.memory = memory;

        String[] colNames = {"Address", "Instruction"};
        this.disassemblyTableModel = new DefaultTableModel(colNames, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
              return false;
          }
        };

        disassembleMemory();

        this.disassemblyTable = new JTable(disassemblyTableModel);
        // remove default keybinding, our table isn't editable anyway!
        this.disassemblyTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F2"), "none");

        this.disassemblyTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        this.disassemblyTableScrollPane = new JScrollPane(disassemblyTable);
    }

    /**
     * Fetches the JScrollPane for adding to a JFrame and showing.
     * @return
     */
    public JScrollPane getDisassemblyTableScrollPane() {
        return this.disassemblyTableScrollPane;
    }

    /**
     * If the memory bank switched while stepping or emulator was unpaused, we drop the entire table
     * and remake it.
     */
    public void redisassembleROM() {
        // listener to MBC, re-disassembles memory bank and replaces
        // disassemblyTableModel.fireTableDataChanged(); // what the fuck?

        this.disassemblyTableModel.setRowCount(0);
        disassembleMemory();
    }

    public void highlightRow(int pc) {
        this.disassemblyTable.clearSelection();
        //this.disassemblyTable.changeSelection(row, 0, false, false);

        String searchString = String.format("$%04X", pc);
        for (int i = 0; i < disassemblyTableModel.getRowCount(); i++) {
            if (disassemblyTableModel.getValueAt(i, 0)
                    .toString()
                    .startsWith(searchString)) {
                disassemblyTable.changeSelection(i, 0, false, false);
                break;
            }
        }
    }

    /**
     * Loops through the memory and disassembles every instruction.
     */
    private void disassembleMemory() {
        // disassembles entire memory
        int programCounter = 0;

        while (programCounter < 0x10000) {

            String[] newRow = new String[2];

            programCounter = disassembleInstruction(programCounter, newRow);
            disassemblyTableModel.addRow(newRow);
            programCounter++;
        }
    }

    /**
     * Disassembles a single instruction at the memory address passed it, and adds it to the disassemblyTableModel.
     * TODO Maybe generalize this so it can be used for replacing single lines? Programs can write functions to HRAM
     *  and we can also switch ROM banks.
     * @param programCounter
     * @return updated programCounter after disassembling an instruction
     */
    private int disassembleInstruction(int programCounter, String[] newRow) {

        int firstNibble = (memory.unconditionalRead(programCounter) & 0xF0) >> 4;
        int secondNibble = (memory.unconditionalRead(programCounter) & 0xF);

        newRow[0] = String.format("$%04X  %02X", programCounter, memory.unconditionalRead(programCounter));
        byte s8 = 0;
        int d8 = 0;
        int d16 = 0;

        if (programCounter < 0xFFFF) {
            s8 = (byte) memory.unconditionalRead(programCounter + 1);
            d8 = memory.unconditionalRead(programCounter + 1);
        }
        if (programCounter < 0xFFFE) {
            d16 = (memory.unconditionalRead(programCounter + 2) << 8) | memory.unconditionalRead(programCounter + 1);
        }

        Reg sourceReg = Registers.getSourceRegByNibble(secondNibble);
        String sourceRegString = Reg.toString(sourceReg);

        switch (firstNibble) {
            case 0x0:
                switch (secondNibble) {
                    case 0x0:
                        newRow[1] = "NOP";
                        break;
                    case 0x1:
                        programCounter += 2;
                        newRow[1] =  String.format("LD %s, $%04X", r16matchFirstNibble(firstNibble), d16);
                        break;
                    case 0x2:
                        newRow[1] =  String.format("LD %s, A", r16ptrMatchFirstNibble(firstNibble));
                        break;
                    case 0x3:
                        newRow[1] =  String.format("INC %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x4:
                        newRow[1] =  String.format("INC %s", r8matchFirstNibble(firstNibble));
                        break;
                    case 0x5:
                        newRow[1] =  String.format("DEC %s", r8matchFirstNibble(firstNibble));
                        break;
                    case 0x6:
                        programCounter += 1;
                        newRow[1] =  String.format("LD %s, $%02X", r8matchFirstNibble(firstNibble), d8);
                        break;
                    case 0x7:
                        newRow[1] =  "RLCA";
                        break;
                    case 0x8:
                        programCounter += 2;
                        newRow[1] =  String.format("LD ($%04X), SP", d16);
                        break;
                    case 0x9:
                        newRow[1] =  String.format("ADD HL %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0xA:
                        newRow[1] =  String.format("LD A, %s", r16ptrMatchFirstNibble(firstNibble));
                        break;
                    case 0xB:
                        newRow[1] =  String.format("DEC %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0xC:
                        newRow[1] =  String.format("INC %s", otherR8matchFirstNibble(firstNibble));
                        break;
                    case 0xD:
                        newRow[1] =  String.format("DEC %s", otherR8matchFirstNibble(firstNibble));
                        break;
                    case 0xE:
                        programCounter += 1;
                        newRow[1] =  String.format("LD %s, $%02X", otherR8matchFirstNibble(firstNibble), d8);
                        break;
                    case 0xF:
                        newRow[1] =  "RRCA";
                        break;
                }
                break;
            case 0x1:
                switch (secondNibble) {
                    case 0x0:
                        programCounter += 1;
                        newRow[1] =  "STOP";
                        break;
                    case 0x1:
                        programCounter += 2;
                        newRow[1] =  String.format("LD %s, $%04X", r16matchFirstNibble(firstNibble), d16);
                        break;
                    case 0x2:
                        newRow[1] =  String.format("LD %s, A", r16ptrMatchFirstNibble(firstNibble));
                        break;
                    case 0x3:
                        newRow[1] =  String.format("INC %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x4:
                        newRow[1] =  String.format("INC %s", r8matchFirstNibble(firstNibble));
                        break;
                    case 0x5:
                        newRow[1] =  String.format("DEC %s", r8matchFirstNibble(firstNibble));
                        break;
                    case 0x6:
                        programCounter += 1;
                        newRow[1] =  String.format("LD %s, $%02X", r8matchFirstNibble(firstNibble), d8);
                        break;
                    case 0x7:
                        newRow[1] =  "RLA";
                        break;
                    case 0x8:
                        programCounter += 1;
                        newRow[1] =  String.format("JR $%04X", (programCounter + s8 + 1));
                        break;
                    case 0x9:
                        newRow[1] =  String.format("ADD HL %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0xA:
                        newRow[1] =  String.format("LD A, %s", r16ptrMatchFirstNibble(firstNibble));
                        break;
                    case 0xB:
                        newRow[1] =  String.format("DEC %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0xC:
                        newRow[1] =  String.format("INC %s", otherR8matchFirstNibble(firstNibble));
                        break;
                    case 0xD:
                        newRow[1] =  String.format("DEC %s", otherR8matchFirstNibble(firstNibble));
                        break;
                    case 0xE:
                        programCounter += 1;
                        newRow[1] =  String.format("LD %s, $%02X", otherR8matchFirstNibble(firstNibble), d8);
                        break;
                    case 0xF:
                        newRow[1] =  "RRA";
                        break;
                }
                break;
            case 0x2:
                switch (secondNibble) {
                    case 0x0:
                        programCounter += 1;
                        newRow[1] =  String.format("JR NZ, $%04X", (programCounter + s8 + 1));
                        break;
                    case 0x1:
                        programCounter += 2;
                        newRow[1] =  String.format("LD %s, $%04X", r16matchFirstNibble(firstNibble), d16);
                        break;
                    case 0x2:
                        newRow[1] =  String.format("LD %s, A", r16ptrMatchFirstNibble(firstNibble));
                        break;
                    case 0x3:
                        newRow[1] =  String.format("INC %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x4:
                        newRow[1] =  String.format("INC %s", r8matchFirstNibble(firstNibble));
                        break;
                    case 0x5:
                        newRow[1] =  String.format("DEC %s", r8matchFirstNibble(firstNibble));
                        break;
                    case 0x6:
                        programCounter += 1;
                        newRow[1] =  String.format("LD %s, $%02X", r8matchFirstNibble(firstNibble), d8);
                        break;
                    case 0x7:
                        newRow[1] =  "DAA";
                        break;
                    case 0x8:
                        programCounter += 1;
                        newRow[1] =  String.format("JR Z, $%04X", (programCounter + s8 + 1));
                        break;
                    case 0x9:
                        newRow[1] =  String.format("ADD HL %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0xA:
                        newRow[1] =  String.format("LD A, %s", r16ptrMatchFirstNibble(firstNibble));
                        break;
                    case 0xB:
                        newRow[1] =  String.format("DEC %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0xC:
                        newRow[1] =  String.format("INC %s", otherR8matchFirstNibble(firstNibble));
                        break;
                    case 0xD:
                        newRow[1] =  String.format("DEC %s", otherR8matchFirstNibble(firstNibble));
                        break;
                    case 0xE:
                        programCounter += 1;
                        newRow[1] =  String.format("LD %s, $%02X", otherR8matchFirstNibble(firstNibble), d8);
                        break;
                    case 0xF:
                        newRow[1] =  "CPL";
                        break;
                }
                break;
            case 0x3:
                switch (secondNibble) {
                    case 0x0:
                        programCounter += 1;
                        newRow[1] =  String.format("JR NC, $%04X", (programCounter + s8 + 1));
                        break;
                    case 0x1:
                        programCounter += 2;
                        newRow[1] =  String.format("LD %s, $%04X", r16matchFirstNibble(firstNibble), d16);
                        break;
                    case 0x2:
                        newRow[1] =  String.format("LD %s, A", r16ptrMatchFirstNibble(firstNibble));
                        break;
                    case 0x3:
                        newRow[1] =  String.format("INC %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x4:
                        newRow[1] =  String.format("INC %s", r8matchFirstNibble(firstNibble));
                        break;
                    case 0x5:
                        newRow[1] =  String.format("DEC %s", r8matchFirstNibble(firstNibble));
                        break;
                    case 0x6:
                        programCounter += 1;
                        newRow[1] =  String.format("LD %s, $%02X", r8matchFirstNibble(firstNibble), d8);
                        break;
                    case 0x7:
                        newRow[1] =  "SCF";
                        break;
                    case 0x8:
                        programCounter += 1;
                        newRow[1] =  String.format("JR C, $%04X", (programCounter + s8 + 1));
                        break;
                    case 0x9:
                        newRow[1] =  String.format("ADD HL %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0xA:
                        newRow[1] =  String.format("LD A, %s", r16ptrMatchFirstNibble(firstNibble));
                        break;
                    case 0xB:
                        newRow[1] =  String.format("DEC %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0xC:
                        newRow[1] =  String.format("INC %s", otherR8matchFirstNibble(firstNibble));
                        break;
                    case 0xD:
                        newRow[1] =  String.format("DEC %s", otherR8matchFirstNibble(firstNibble));
                        break;
                    case 0xE:
                        programCounter += 1;
                        newRow[1] =  String.format("LD %s, $%02X", otherR8matchFirstNibble(firstNibble), d8);
                        break;
                    case 0xF:
                        newRow[1] =  "CCF";
                        break;
                }
                break;
            case 0x4:
                if (secondNibble <= 0x7) {
                    newRow[1] =  String.format("LD B, %s", sourceRegString);
                } else {
                    newRow[1] =  String.format("LD C, %s", sourceRegString);
                }
                break;
            case 0x5:
                if (secondNibble <= 0x7) {
                    newRow[1] =  String.format("LD D, %s", sourceRegString);
                } else {
                    newRow[1] =  String.format("LD E, %s", sourceRegString);
                }
                break;
            case 0x6:
                if (secondNibble <= 0x7) {
                    newRow[1] =  String.format("LD H, %s", sourceRegString);
                } else {
                    newRow[1] =  String.format("LD L, %s", sourceRegString);
                }
                break;
            case 0x7:
                if (secondNibble <= 0x5 || secondNibble == 0x7) {
                    newRow[1] =  String.format("LD (HL), %s", sourceRegString);
                } else if (secondNibble == 0x6) {
                    newRow[1] =  "HALT";
                } else {
                    newRow[1] =  String.format("LD A, %s", sourceRegString);
                }
                break;
            case 0x8:
                if (secondNibble <= 0x7) {
                    newRow[1] =  String.format("ADD A, %s", sourceRegString);
                } else {
                    newRow[1] =  String.format("ADC A, %s", sourceRegString);
                }
                break;
            case 0x9:
                if (secondNibble <= 0x7) {
                    newRow[1] =  String.format("SUB %s", sourceRegString);
                } else {
                    newRow[1] =  String.format("SBC A, %s", sourceRegString);
                }
                break;
            case 0xA:
                if (secondNibble <= 0x7) {
                    newRow[1] =  String.format("AND %s", sourceRegString);
                } else {
                    newRow[1] =  String.format("XOR %s", sourceRegString);
                }
                break;
            case 0xB:
                if (secondNibble <= 0x7) {
                    newRow[1] =  String.format("OR %s", sourceRegString);
                } else {
                    newRow[1] =  String.format("CP %s", sourceRegString);
                }
                break;
            case 0xC:
                switch (secondNibble) {
                    case 0x0:
                        newRow[1] =  "RET NZ";
                        break;
                    case 0x1:
                        newRow[1] =  String.format("POP %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x2:
                        programCounter += 2;
                        newRow[1] =  String.format("JP NZ, ($%04X)", d16);
                        break;
                    case 0x3:
                        programCounter += 2;
                        newRow[1] =  String.format("JP $%04X", d16);
                        break;
                    case 0x4:
                        programCounter += 2;
                        newRow[1] =  String.format("CALL NZ, $%04X", d16);
                        break;
                    case 0x5:
                        newRow[1] =  String.format("PUSH, %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x6:
                        programCounter += 1;
                        newRow[1] =  String.format("ADD A, $%02X", d8);
                        break;
                    case 0x7:
                        newRow[1] = "RST 0";
                        break;
                    case 0x8:
                        newRow[1] = "RET Z";
                        break;
                    case 0x9:
                        newRow[1] = "RET";
                        break;
                    case 0xA:
                        programCounter += 2;
                        newRow[1] = String.format("JP Z, $%04X", d16);
                        break;
                    case 0xB:
                        programCounter += 1;
                        newRow[1] = disassembleBigInstruction(programCounter);
                        break;
                    case 0xC:
                        programCounter += 2;
                        newRow[1] = String.format("CALL Z, $%04X", d16);
                        break;
                    case 0xD:
                        programCounter += 2;
                        newRow[1] = String.format("CALL $%04X", d16);
                        break;
                    case 0xE:
                        programCounter += 1;
                        newRow[1] = String.format("ADC A, %02X", d8);
                        break;
                    case 0xF:
                        newRow[1] = "RST 1";
                        break;
                }
                break;
            case 0xD:
                switch (secondNibble) {
                    case 0x0:
                        newRow[1] = "RET NC";
                        break;
                    case 0x1:
                        newRow[1] = String.format("POP %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x2:
                        programCounter += 2;
                        newRow[1] = String.format("JP NC, ($%04X)", d16);
                        break;
                    case 0x4:
                        programCounter += 2;
                        newRow[1] = String.format("CALL NC, $%04X", d16);
                        break;
                    case 0x5:
                        newRow[1] = String.format("PUSH, %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x6:
                        programCounter += 1;
                        newRow[1] = String.format("SUB $%02X", d8);
                        break;
                    case 0x7:
                        newRow[1] = "RST 2";
                        break;
                    case 0x8:
                        newRow[1] = "RET C";
                        break;
                    case 0x9:
                        newRow[1] = "RETI";
                        break;
                    case 0xA:
                        programCounter += 2;
                        newRow[1] = String.format("JP C, $%04X", d16);
                        break;
                    case 0xC:
                        programCounter += 2;
                        newRow[1] = String.format("CALL C, $%04X", d16);
                        break;
                    case 0xE:
                        programCounter += 1;
                        newRow[1] = String.format("SBC A, %02X", d8);
                        break;
                    case 0xF:
                        newRow[1] = "RST 3";
                        break;
                    default:
                        newRow[1] = "UNDEFINED OPCODE";
                }
                break;
            case 0xE:
                switch (secondNibble) {
                    case 0x0:
                        programCounter += 1;
                        newRow[1] = String.format("LD ($%02X), A", d8);
                        break;
                    case 0x1:
                        newRow[1] = String.format("POP %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x2:
                        newRow[1] = "LD (C), A";
                        break;
                    case 0x5:
                        newRow[1] = String.format("PUSH, %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x6:
                        programCounter += 1;
                        newRow[1] = String.format("AND %02X", d8);
                        break;
                    case 0x7:
                        newRow[1] = "RST 4";
                        break;
                    case 0x8:
                        programCounter += 1;
                        newRow[1] = String.format("ADD SP, %d", s8);
                        break;
                    case 0x9:
                        newRow[1] = "JP HL";
                        break;
                    case 0xA:
                        programCounter += 2;
                        newRow[1] = String.format("LD ($%04X), A", d16);
                        break;

                    case 0xE:
                        programCounter += 1;
                        newRow[1] = String.format("XOR %02X", d8);
                        break;
                    case 0xF:
                        newRow[1] = "RST 5";
                        break;
                    default:
                        newRow[1] = "UNDEFINED OPCODE";
                }
                break;
            case 0xF:
                switch (secondNibble) {
                    case 0x0:
                        programCounter += 1;
                        newRow[1] = String.format("LD A, ($%02X)", d8);
                        break;
                    case 0x1:
                        newRow[1] = String.format("POP %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x2:
                        newRow[1] = "LD A, (C)";
                        break;
                    case 0x3:
                        newRow[1] = "DI";
                        break;
                    case 0x5:
                        newRow[1] = String.format("PUSH, %s", r16matchFirstNibble(firstNibble));
                        break;
                    case 0x6:
                        programCounter += 1;
                        newRow[1] = String.format("OR $%02X", d8);
                        break;
                    case 0x7:
                        newRow[1] = "RST 6";
                        break;
                    case 0x8:
                        programCounter += 1;
                        newRow[1] = String.format(s8 > 0 ? "LD HL, SP+%d" : "LD HL, SP%d", s8);
                        break;
                    case 0x9:
                        newRow[1] = "LD SP, HL";
                        break;
                    case 0xA:
                        programCounter += 2;
                        newRow[1] = String.format("LD A, ($%04X)", d16);
                        break;
                    case 0xB:
                        newRow[1] = "EI";
                        break;
                    case 0xE:
                        programCounter += 1;
                        newRow[1] = String.format("CP %02X", d8);
                        break;
                    case 0xF:
                        newRow[1] = "RST 7";
                        break;
                    default:
                        newRow[1] = "UNDEFINED OPCODE";
                }
                break;
        }
        return programCounter;
    }

    private String r16matchFirstNibble(int firstNibble) {
        return switch (firstNibble % 0xC) {
            case 0 -> "BC";
            case 1 -> "DE";
            case 2 -> "HL";
            case 3 -> "SP";
            default -> ""; // unreachable
        };
    }
    
    private String r16ptrMatchFirstNibble(int firstNibble) {
        return switch (firstNibble) {
            case 0 -> "(BC)";
            case 1 -> "(DE)";
            case 2 -> "(HL+)";
            case 3 -> "(HL-)";
            default -> ""; // unreachable
        };
    }

    private String r8matchFirstNibble(int firstNibble) {
        return switch (firstNibble) {
            case 0 -> "B";
            case 1 -> "D";
            case 2 -> "H";
            case 3 -> "(HL)";
            default -> ""; // unreachable
        };
    }

    private String otherR8matchFirstNibble(int firstNibble) {
        return switch (firstNibble) {
            case 0 -> "C";
            case 1 -> "E";
            case 2 -> "L";
            case 3 -> "A";
            default -> ""; // unreachable
        };
    }

    /**
     * Separate switch statement for disassembling the 0xCB instructions.
     * @param programCounter (0xCB + 1)
     * @return stringified instruction at given programCounter (0xCB + 1)
     */
    private String disassembleBigInstruction(int programCounter) {
        int firstNibble = (memory.unconditionalRead(programCounter) & 0xF0) >> 4;
        int secondNibble = memory.unconditionalRead(programCounter) & 0x0F;

        Reg sourceReg = Registers.getSourceRegByNibble(secondNibble);
        String sourceRegString = Reg.toString(sourceReg);

        switch (firstNibble) {
            case 0x0:
                if (secondNibble <= 0x7) {
                    return String.format("RLC %s", sourceRegString);
                } else {
                    return String.format("RRC %s", sourceRegString);
                }
            case 0x1:
                if (secondNibble <= 0x7) {
                    return String.format("RL %s", sourceRegString);
                } else {
                    return String.format("RR %s", sourceRegString);
                }
            case 0x2:
                if (secondNibble <= 0x7) {
                    return String.format("SLA %s", sourceRegString);
                } else {
                    return String.format("SRA %s", sourceRegString);
                }
            case 0x3:
                if (secondNibble <= 0x7) {
                    return String.format("SWAP %s", sourceRegString);
                } else {
                    return String.format("SRL %s", sourceRegString);
                }
            case 0x4:
                if (secondNibble <= 0x7) {
                    return String.format("BIT 0, %s", sourceRegString);
                } else {
                    return String.format("BIT 1, %s", sourceRegString);
                }
            case 0x5:
                if (secondNibble <= 0x7) {
                    return String.format("BIT 2, %s", sourceRegString);
                } else {
                    return String.format("BIT 3, %s", sourceRegString);
                }
            case 0x6:
                if (secondNibble <= 0x7) {
                    return String.format("BIT 4, %s", sourceRegString);
                } else {
                    return String.format("BIT 5, %s", sourceRegString);
                }
            case 0x7:
                if (secondNibble <= 0x7) {
                    return String.format("BIT 6, %s", sourceRegString);
                } else {
                    return String.format("BIT 7, %s", sourceRegString);
                }
            case 0x8:
                if (secondNibble <= 0x7) {
                    return String.format("RES 0, %s", sourceRegString);
                } else {
                    return String.format("RES 1, %s", sourceRegString);
                }
            case 0x9:
                if (secondNibble <= 0x7) {
                    return String.format("RES 2, %s", sourceRegString);
                } else {
                    return String.format("RES 3, %s", sourceRegString);
                }
            case 0xA:
                if (secondNibble <= 0x7) {
                    return String.format("RES 4, %s", sourceRegString);
                } else {
                    return String.format("RES 5, %s", sourceRegString);
                }
            case 0xB:
                if (secondNibble <= 0x7) {
                    return String.format("RES 6, %s", sourceRegString);
                } else {
                    return String.format("RES 7, %s", sourceRegString);
                }
            case 0xC:
                if (secondNibble <= 0x7) {
                    return String.format("SET 0, %s", sourceRegString);
                } else {
                    return String.format("SET 1, %s", sourceRegString);
                }
            case 0xD:
                if (secondNibble <= 0x7) {
                    return String.format("SET 2, %s", sourceRegString);
                } else {
                    return String.format("SET 3, %s", sourceRegString);
                }
            case 0xE:
                if (secondNibble <= 0x7) {
                    return String.format("SET 4 %s", sourceRegString);
                } else {
                    return String.format("SET 5, %s", sourceRegString);
                }
            case 0xF:
                if (secondNibble <= 0x7) {
                    return String.format("SET 6, %s", sourceRegString);
                } else {
                    return String.format("SET 7, %s", sourceRegString);
                }
        }
        return ""; // unreachable
    }
}
