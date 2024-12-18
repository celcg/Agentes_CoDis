package codigo;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class VendedorGUI extends JFrame {
    private final Vendedor agente; // Referencia al agente
    private final JTextArea areaNotificaciones; // Área de texto para mensajes
    private final JTextField txtTitulo, txtPrecioInicial, txtIncremento;
    private final DefaultTableModel modeloActivas; // Modelo para subastas activas
    private final DefaultTableModel modeloFinalizadas; // Modelo para subastas finalizadas

    public VendedorGUI(Vendedor agente) {
        this.agente = agente;

        // Configurar la ventana
        setTitle("Interfaz del Vendedor");
        setSize(800, 600); // Tamaño ajustado para incluir las tablas
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Evita el cierre automático

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleExitAction(); // Llama a un método personalizado antes de cerrar
            }
        });
        setLayout(new BorderLayout());

        // Área de mensajes
        areaNotificaciones = new JTextArea();
        areaNotificaciones.setEditable(false); // Solo lectura
        JScrollPane scrollMensajes = new JScrollPane(areaNotificaciones);
        scrollMensajes.setBorder(BorderFactory.createTitledBorder("Notificaciones"));
        // Configurar la altura del área de mensajes para no tapar la vista
        scrollMensajes.setPreferredSize(new Dimension(800, 100));

        // Panel de formulario para iniciar subastas
        JPanel panelFormulario = new JPanel(new GridBagLayout());
        panelFormulario.setBorder(BorderFactory.createTitledBorder("Iniciar Nueva Subasta"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5); // Espaciado entre componentes
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Título del libro
        gbc.gridx = 0;
        gbc.gridy = 0;
        panelFormulario.add(new JLabel("Título del libro:"), gbc);

        gbc.gridx = 1;
        txtTitulo = new JTextField(20);
        panelFormulario.add(txtTitulo, gbc);

        // Precio inicial
        gbc.gridx = 0;
        gbc.gridy = 1;
        panelFormulario.add(new JLabel("Precio Inicial:"), gbc);

        gbc.gridx = 1;
        txtPrecioInicial = new JTextField(20);
        panelFormulario.add(txtPrecioInicial, gbc);

        // Incremento
        gbc.gridx = 0;
        gbc.gridy = 2;
        panelFormulario.add(new JLabel("Incremento:"), gbc);

        gbc.gridx = 1;
        txtIncremento = new JTextField(20);
        panelFormulario.add(txtIncremento, gbc);

        // Botón para iniciar subasta
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton btnIniciarSubasta = new JButton("Iniciar Subasta");
        panelFormulario.add(btnIniciarSubasta, gbc);

        // Añadir listeners
        btnIniciarSubasta.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String titulo = txtTitulo.getText().trim();
                    float precioInicial = Float.parseFloat(txtPrecioInicial.getText().trim());
                    float incremento = Float.parseFloat(txtIncremento.getText().trim());

                    if (titulo.isEmpty()) {
                        agregarNotificacion("Error: El título del libro no puede estar vacío.");
                        JOptionPane.showMessageDialog(VendedorGUI.this, "El título del libro no puede estar vacío.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }

                    // Llamar al método del agente para iniciar la subasta
                    agente.iniciarSubasta(titulo, precioInicial, incremento);
                    agregarSubastaATabla(titulo,precioInicial, incremento, "", 0);

                    // Limpiar los campos de texto
                    txtTitulo.setText("");
                    txtPrecioInicial.setText("");
                    txtIncremento.setText("");

                } catch (NumberFormatException ex) {
                    agregarNotificacion("Error: Precio inicial e incremento deben ser números.");
                    JOptionPane.showMessageDialog(VendedorGUI.this, "Precio inicial e incremento deben ser números.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Tablas de subastas
        // Crear el modelo para subastas activas
        modeloActivas = new DefaultTableModel(new String[]{"Subasta", "Precio (ronda)", "Incremento", "Pujadores", "Ronda"}, 0);
        // Tabla para subastas activas
        JTable tablaSubastasActivas = new JTable(modeloActivas);
        JScrollPane scrollActivas = new JScrollPane(tablaSubastasActivas);
        scrollActivas.setBorder(BorderFactory.createTitledBorder("Subastas Activas"));

        // Crear el modelo para subastas finalizadas
        modeloFinalizadas = new DefaultTableModel(new String[]{"Título", "Ganador", "Precio Final", "Telefono"}, 0);
        // Tabla para subastas finalizadas
        JTable tablaSubastasFinalizadas = new JTable(modeloFinalizadas);
        JScrollPane scrollFinalizadas = new JScrollPane(tablaSubastasFinalizadas);
        scrollFinalizadas.setBorder(BorderFactory.createTitledBorder("Subastas Finalizadas"));

        // Panel de Tablas con JTabbedPane para mostrar las dos vistas
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Activas", scrollActivas);
        tabbedPane.addTab("Finalizadas", scrollFinalizadas);

        // Panel principal para dividir los elementos
        JPanel panelPrincipal = new JPanel(new BorderLayout());
        panelPrincipal.add(tabbedPane, BorderLayout.CENTER);
        panelPrincipal.add(scrollMensajes, BorderLayout.SOUTH);

        // Añadir componentes al JFrame
        add(panelFormulario, BorderLayout.NORTH);
        add(panelPrincipal, BorderLayout.CENTER);
    }

    // Método para mostrar mensajes en el área de texto
    public void agregarNotificacion(String mensaje) {
        areaNotificaciones.append(mensaje + "\n");
        areaNotificaciones.setCaretPosition(areaNotificaciones.getDocument().getLength());
    }

    // Método para agregar una subasta a la tabla de activas
    public void agregarSubastaATabla(String titulo, float precioActual, float incremento, String pujadores, int ronda) {
        // Agregar una nueva fila a la tabla de subastas activas
        modeloActivas.addRow(new Object[]{titulo, precioActual, incremento, pujadores, ronda});
    }

    // Método para actualizar una subasta en la tabla de activas
    public void actualizarSubastaEnTabla(String titulo, float precioActual, float incremento, String pujadores, int ronda) {
        for (int i = 0; i < modeloActivas.getRowCount(); i++) {
            if (modeloActivas.getValueAt(i, 0).equals(titulo)) {
                modeloActivas.setValueAt(precioActual, i, 1);   // Actualizar precio actual
                modeloActivas.setValueAt(incremento, i, 2);      // Actualizar incremento
                modeloActivas.setValueAt(pujadores, i, 3);      // Actualizar pujadores
                modeloActivas.setValueAt(ronda, i, 4);          // Actualizar ronda
                break;
            }
        }
    }


    // Método para notificar cuando un libro ha sido vendido y moverlo a la tabla de finalizadas
    public void notificarLibroVendido(String titulo, String comprador, float precioFinal, String telefono) {
        // Mover la subasta de Activas a Finalizadas
        for (int i = 0; i < modeloActivas.getRowCount(); i++) {
            if (modeloActivas.getValueAt(i, 0).equals(titulo)) {
                modeloFinalizadas.addRow(new Object[]{titulo, comprador, precioFinal, telefono});
                modeloActivas.removeRow(i);
                break;
            }
        }
    }

    private void handleExitAction() {
        agente.notificarSalida();

        // Luego cierra la ventana
        dispose(); // Libera los recursos y cierra la ventana
    }

}
