package com.anthonyponte.jbillconsultservice.controller;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.SortedList;
import ca.odell.glazedlists.TextFilterator;
import ca.odell.glazedlists.gui.TableFormat;
import ca.odell.glazedlists.matchers.MatcherEditor;
import ca.odell.glazedlists.swing.AdvancedListSelectionModel;
import ca.odell.glazedlists.swing.AdvancedTableModel;
import ca.odell.glazedlists.swing.DefaultEventSelectionModel;
import static ca.odell.glazedlists.swing.GlazedListsSwing.eventTableModelWithThreadProxyList;
import ca.odell.glazedlists.swing.TableComparatorChooser;
import ca.odell.glazedlists.swing.TextComponentMatcherEditor;
import com.anthonyponte.jbillconsultservice.pojo.Bill;
import com.anthonyponte.jbillconsultservice.impl.BillServiceImpl;
import com.anthonyponte.jbillconsultservice.view.BillFrame;
import com.anthonyponte.jbillconsultservice.view.LoadingDialog;
import com.anthonyponte.jbillconsultservice.view.UsuarioFrame;
import com.poiji.bind.Poiji;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.kordamp.ikonli.remixicon.RemixiconMZ;
import org.kordamp.ikonli.swing.FontIcon;
import sunat.gob.pe.BillService;
import sunat.gob.pe.StatusResponse;

public class BillController {

  private final BillFrame frame;
  private LoadingDialog dialog;
  private BillService service;
  private String os;
  private EventList<Bill> eventList;
  private SortedList<Bill> sortedList;
  private AdvancedListSelectionModel<Bill> selectionModel;
  private AdvancedTableModel<Bill> model;

  public BillController(BillFrame frame) {
    this.frame = frame;
    initComponents();
  }

  public void init() {
    frame.miImportar.addActionListener(
        (ActionEvent arg0) -> {
          JFileChooser chooser = new JFileChooser();
          chooser.setDialogTitle("Importar");
          chooser.setApproveButtonText("Importar");
          chooser.setAcceptAllFileFilterUsed(false);
          chooser.addChoosableFileFilter(
              new FileNameExtensionFilter("Archivo Excel", "xls", "xlsx"));
          chooser.setCurrentDirectory(new File("."));

          int result = chooser.showOpenDialog(frame);
          if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            setToTable(file);
          }
        });

    frame.miExportar.addActionListener(
        (ActionEvent arg0) -> {
          SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
          String dateString = format.format(new Date());

          JFileChooser chooser = new JFileChooser();
          chooser.setDialogTitle("Exportar");
          chooser.setApproveButtonText("Exportar");
          chooser.setAcceptAllFileFilterUsed(false);
          chooser.addChoosableFileFilter(new FileNameExtensionFilter("Archivo Excel", "xlsx"));
          chooser.setSelectedFile(new File(dateString.concat(".xlsx")));
          chooser.setCurrentDirectory(new File("."));

          int result = chooser.showSaveDialog(frame);
          if (result == JFileChooser.APPROVE_OPTION) {

            SwingWorker worker =
                new SwingWorker<XSSFWorkbook, Integer>() {
                  @Override
                  protected XSSFWorkbook doInBackground() throws Exception {

                    dialog.setVisible(true);
                    dialog.setLocationRelativeTo(frame);
                    dialog.progressBar.setMinimum(0);
                    dialog.progressBar.setMaximum(model.getRowCount());

                    XSSFWorkbook workbook = new XSSFWorkbook();
                    XSSFSheet sheet = workbook.createSheet("Comprobantes");

                    for (int r = 0; r < model.getRowCount(); r++) {
                      XSSFRow row = sheet.createRow(r);
                      for (int c = 0; c < model.getColumnCount(); c++) {
                        XSSFCell cell = row.createCell(c);
                        if (r == 0) {
                          cell.setCellValue(model.getColumnName(c));
                        }
                      }
                    }

                    for (int r = 0; r < model.getRowCount(); r++) {
                      XSSFRow row = sheet.createRow(r + 1);
                      Bill bill = model.getElementAt(r);
                      publish(r);

                      for (int c = 0; c < model.getColumnCount(); c++) {
                        XSSFCell cell = row.createCell(c);
                        sheet.autoSizeColumn(c);

                        switch (cell.getColumnIndex()) {
                          case 0:
                            cell.setCellValue(bill.getRuc());
                            break;
                          case 1:
                            cell.setCellValue(bill.getTipo());
                            break;
                          case 2:
                            cell.setCellValue(bill.getSerie());
                            break;
                          case 3:
                            cell.setCellValue(bill.getNumero());
                            break;
                          case 4:
                            cell.setCellValue(bill.getCdrStatusCode());
                            break;
                          case 5:
                            cell.setCellValue(bill.getStatusMessage());
                            break;
                          default:
                            break;
                        }
                      }
                    }

                    return workbook;
                  }

                  @Override
                  protected void process(List<Integer> chunks) {
                    dialog.progressBar.setValue(chunks.get(0));
                  }

                  @Override
                  protected void done() {
                    try {
                      XSSFWorkbook get = get();
                      File file = chooser.getSelectedFile();

                      try (FileOutputStream out = new FileOutputStream(file)) {
                        get.write(out);
                      }

                      dialog.dispose();

                      if (os.compareToIgnoreCase("linux") < 0) {
                        showNotification(
                            "Se exporto correctamente el archivo en la ruta "
                                + file.getAbsolutePath(),
                            MessageType.INFO);
                      }
                    } catch (InterruptedException | ExecutionException | IOException ex) {
                      JOptionPane.showMessageDialog(
                          frame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                  }
                };

            worker.execute();
          }
        });

    frame.miSalir.addActionListener(
        (ActionEvent arg0) -> {
          int input =
              JOptionPane.showConfirmDialog(
                  frame,
                  "Seguro que desea salir?",
                  "Salir",
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE);
          if (input == JOptionPane.YES_OPTION) {
            frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
          }
        });

    frame.scroll.setDropTarget(
        new DropTarget() {
          @Override
          public synchronized void drop(DropTargetDropEvent dtde) {
            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
              try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                Transferable t = dtde.getTransferable();
                List fileList = (List) t.getTransferData(DataFlavor.javaFileListFlavor);

                if (fileList != null && !fileList.isEmpty()) {
                  for (Object value : fileList) {
                    if (value instanceof File) {
                      File file = (File) value;
                      setToTable(file);
                    }
                  }
                }
              } catch (UnsupportedFlavorException | IOException ex) {
                JOptionPane.showMessageDialog(
                    frame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
              }
            } else {
              dtde.rejectDrop();
            }
          }
        });

    frame.table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              Bill selected = selectionModel.getSelected().get(0);

              if (selected.getStatusCode().equals("0001")
                  || selected.getStatusCode().equals("0002")
                  || selected.getStatusCode().equals("0003")) {

                SwingWorker worker =
                    new SwingWorker<Bill, Integer>() {
                      @Override
                      protected Bill doInBackground() throws Exception {
                        dialog.setVisible(true);
                        dialog.setLocationRelativeTo(frame);

                        dialog.progressBar.setMinimum(0);
                        dialog.progressBar.setMaximum(100);

                        publish(0);
                        StatusResponse statusResponse =
                            service.getStatusCdr(
                                selected.getRuc(),
                                selected.getTipo(),
                                selected.getSerie(),
                                selected.getNumero());

                        selected.setCdrStatusCode(statusResponse.getStatusCode());
                        selected.setCdrStatusMessage(statusResponse.getStatusMessage());
                        selected.setCdrContent(statusResponse.getContent());
                        publish(100);

                        return selected;
                      }

                      @Override
                      protected void process(List<Integer> chunks) {
                        dialog.progressBar.setValue(chunks.get(0));
                      }

                      @Override
                      protected void done() {
                        try {
                          dialog.dispose();

                          Bill get = get();

                          if (os.compareToIgnoreCase("linux") < 0) {
                            showNotification(
                                get.getCdrStatusCode() + " - " + get.getCdrStatusMessage(),
                                MessageType.INFO);
                          }

                          if (get.getCdrStatusCode().equals("0004")) {
                            JFileChooser chooser = new JFileChooser();
                            chooser.setDialogTitle("Guardar");
                            chooser.setApproveButtonText("Guardar");
                            chooser.setAcceptAllFileFilterUsed(false);
                            chooser.addChoosableFileFilter(
                                new FileNameExtensionFilter("Archivo Zip", "zip"));
                            chooser.setCurrentDirectory(new File("."));
                            chooser.setSelectedFile(
                                new File(
                                    "R-"
                                        + get.getRuc()
                                        + "-"
                                        + get.getNumero()
                                        + "-"
                                        + get.getSerie()
                                        + "-"
                                        + get.getNumero()
                                        + ".zip"));

                            int result = chooser.showSaveDialog(frame);
                            if (result == JFileChooser.APPROVE_OPTION) {
                              File file = chooser.getSelectedFile().getAbsoluteFile();
                              try (FileOutputStream fout =
                                  new FileOutputStream(file.getParent() + "//" + file.getName())) {
                                fout.write(get.getCdrContent());
                                fout.flush();
                                fout.close();
                              } catch (FileNotFoundException ex) {
                                JOptionPane.showMessageDialog(
                                    frame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                              } catch (IOException ex) {
                                JOptionPane.showMessageDialog(
                                    frame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                              }
                            }
                          } else {
                            JOptionPane.showMessageDialog(
                                frame,
                                get.getCdrStatusMessage(),
                                get.getCdrStatusCode(),
                                JOptionPane.ERROR_MESSAGE);
                          }
                        } catch (InterruptedException | ExecutionException ex) {
                          JOptionPane.showMessageDialog(
                              frame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                      }
                    };

                worker.execute();
              }
            }
          }
        });
  }

  private void initComponents() {
    dialog = new LoadingDialog(frame, false);
    service = new BillServiceImpl();
    os = System.getProperty("os.name");
    eventList = new BasicEventList<>();

    Comparator comparator =
        (Comparator<Bill>) (Bill o1, Bill o2) -> o1.getNumero() - o2.getNumero();

    sortedList = new SortedList<>(eventList, comparator);

    TextFilterator<Bill> textFilterator =
        (List<String> baseList, Bill element) -> {
          baseList.add(element.getRuc());
          baseList.add(element.getTipo());
          baseList.add(element.getSerie());
          baseList.add(String.valueOf(element.getNumero()));
          baseList.add(element.getStatusCode());
          baseList.add(element.getStatusMessage());
        };

    MatcherEditor<Bill> matcherEditor =
        new TextComponentMatcherEditor<>(this.frame.tfFiltrar, textFilterator);

    FilterList<Bill> filterList = new FilterList<>(sortedList, matcherEditor);

    TableFormat<Bill> tableFormat =
        new TableFormat<Bill>() {
          @Override
          public int getColumnCount() {
            return 6;
          }

          @Override
          public String getColumnName(int column) {
            switch (column) {
              case 0:
                return "RUC";
              case 1:
                return "Tipo";
              case 2:
                return "Serie";
              case 3:
                return "Numero";
              case 4:
                return "Codigo";
              case 5:
                return "Estado";
              default:
                break;
            }
            throw new IllegalStateException("Unexpected column: " + column);
          }

          @Override
          public Object getColumnValue(Bill baseObject, int column) {
            switch (column) {
              case 0:
                return baseObject.getRuc();
              case 1:
                return baseObject.getTipo();
              case 2:
                return baseObject.getSerie();
              case 3:
                return baseObject.getNumero();
              case 4:
                return baseObject.getStatusCode();
              case 5:
                return baseObject.getStatusMessage();
              default:
                break;
            }
            throw new IllegalStateException("Unexpected column: " + column);
          }
        };

    model = eventTableModelWithThreadProxyList(filterList, tableFormat);

    selectionModel = new DefaultEventSelectionModel<>(filterList);

    frame.table.setModel(model);

    frame.table.setSelectionModel(selectionModel);

    TableComparatorChooser.install(
        frame.table, sortedList, TableComparatorChooser.MULTIPLE_COLUMN_MOUSE);

    frame.setVisible(true);

    frame.table.requestFocus();
  }

  private void setToTable(File file) {
    dialog.setVisible(true);
    dialog.setLocationRelativeTo(frame);

    SwingWorker worker =
        new SwingWorker<List<Bill>, Void>() {
          @Override
          protected List<Bill> doInBackground() throws Exception {
            List<Bill> list = null;
            try {
              list = Poiji.fromExcel(file, Bill.class);
              for (int i = 0; i < list.size(); i++) {
                Bill bill = (Bill) list.get(i);

                StatusResponse statusResponse =
                    service.getStatus(
                        bill.getRuc(), bill.getTipo(), bill.getSerie(), bill.getNumero());

                list.get(i).setStatusCode(statusResponse.getStatusCode());
                list.get(i).setStatusMessage(statusResponse.getStatusMessage());
              }
            } catch (Exception ex) {
              cancel(true);

              JOptionPane.showMessageDialog(
                  null, ex.getMessage(), BillController.class.getName(), JOptionPane.ERROR_MESSAGE);
            }
            return list;
          }

          @Override
          protected void done() {
            dialog.dispose();

            if (!isCancelled()) {
              try {
                List<Bill> get = get();

                eventList.clear();
                eventList.addAll(get);

                resize(frame.table);

                frame.tfFiltrar.requestFocus();

                if (os.compareToIgnoreCase("linux") < 0) {
                  showNotification(
                      "Se consultaron " + get.size() + " comprobantes", MessageType.INFO);
                }

              } catch (InterruptedException | ExecutionException ex) {
                if (os.compareToIgnoreCase("linux") < 0) {
                  showNotification("Error en clave SOL", MessageType.ERROR);
                }

                int input =
                    JOptionPane.showOptionDialog(
                        frame,
                        ex.getMessage(),
                        "Error",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.ERROR_MESSAGE,
                        null,
                        null,
                        null);

                if (input == JOptionPane.OK_OPTION) {
                  frame.dispose();
                  UsuarioFrame userFrame = new UsuarioFrame();
                  new UsuarioController(userFrame).init();
                }
              }
            }
          }
        };

    worker.execute();
  }

  public void resize(JTable table) {
    TableColumnModel columnModel = table.getColumnModel();
    for (int column = 0; column < table.getColumnCount(); column++) {
      int width = 100;
      for (int row = 0; row < table.getRowCount(); row++) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component comp = table.prepareRenderer(renderer, row, column);
        width = Math.max(comp.getPreferredSize().width + 1, width);
      }
      if (width > 300) width = 300;
      columnModel.getColumn(column).setPreferredWidth(width);
    }
  }

  private void showNotification(String message, MessageType type) {
    try {
      SystemTray tray = SystemTray.getSystemTray();
      TrayIcon icon =
          new TrayIcon(
              FontIcon.of(RemixiconMZ.NOTIFICATION_LINE, 16, Color.decode("#FFFFFF"))
                  .toImageIcon()
                  .getImage(),
              "JBillConsultService");
      icon.setImageAutoSize(true);
      icon.displayMessage("JBillStatus", message, type);
      tray.add(icon);
    } catch (AWTException ex) {
      JOptionPane.showMessageDialog(frame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }
  }
}
