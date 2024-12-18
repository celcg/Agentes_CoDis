package codigo;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class CompradorGUI extends JFrame {
    private final JPanel auctionPanelContainer; // Contenedor de paneles para las subastas
    private final JTextField bookTitleField;
    private final JTextField maxPriceField;
    private final JTable interestedBooksTable;
    private final JTable purchasedBooksTable;
    private final DefaultTableModel interestedBooksModel;
    private final DefaultTableModel purchasedBooksModel;
    private final JTextField searchField;  // Campo de texto para búsqueda
    private final JButton searchButton;    // Botón de búsqueda
    private final JTextArea areaMensajes;
    private final JButton showAllButton;
    private final JButton showInterestedButton; // Botón para mostrar solo libros de interés
    private final JButton enterButton; // Botón para Entrar
    private final JButton exitButton;  // Botón para Salir

    private final Comprador comprador;

    // Mapa para almacenar los paneles de cada subasta (por libro)
    private final Map<String, JPanel> bookPanels = new HashMap<>();

    public CompradorGUI(Comprador comp) {
        this.comprador = comp;

        setTitle("codigo.Comprador " + comp.getLocalName());
        setSize(1200, 600); // Tamaño más grande para ajustarse a la nueva disposición
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Evita el cierre automático

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                handleExitAction(); // Llama a un método personalizado antes de cerrar
            }
        });

        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Modelos para las tablas
        interestedBooksModel = new DefaultTableModel(new Object[]{"Título", "Precio Máximo"}, 0);
        purchasedBooksModel = new DefaultTableModel(new Object[]{"Título", "Precio Final"}, 0);

        // Tablas
        interestedBooksTable = new JTable(interestedBooksModel);
        purchasedBooksTable = new JTable(purchasedBooksModel);

        // Crear las tablas dentro de paneles con barras de desplazamiento
        JScrollPane interestedScrollPane = new JScrollPane(interestedBooksTable);
        JScrollPane purchasedScrollPane = new JScrollPane(purchasedBooksTable);

        interestedScrollPane.setBorder(BorderFactory.createTitledBorder("Libros de Interés"));
        purchasedScrollPane.setBorder(BorderFactory.createTitledBorder("Libros Comprados"));

        // Panel izquierdo con las dos tablas
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.add(interestedScrollPane);
        leftPanel.add(purchasedScrollPane);

        // Panel principal para las subastas a la derecha
        auctionPanelContainer = new JPanel();
        auctionPanelContainer.setLayout(new BoxLayout(auctionPanelContainer, BoxLayout.Y_AXIS));
        JScrollPane auctionScrollPane = new JScrollPane(auctionPanelContainer);

        // Disposición general de la ventana
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, auctionScrollPane);
        splitPane.setResizeWeight(0.1); // Aumentamos el peso de la parte derecha (subastas)
        add(splitPane, BorderLayout.CENTER);

        // Panel inferior con un BorderLayout
        JPanel inputPanel = new JPanel(new BorderLayout());

        // Subpanel izquierdo para los campos de texto y botones
        JPanel leftInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)); // Alineación a la izquierda

        // Título del libro
        leftInputPanel.add(new JLabel("Título del libro:"));
        bookTitleField = new JTextField(10); // Tamaño ajustado para el campo de texto
        leftInputPanel.add(bookTitleField);

        // Precio máximo
        leftInputPanel.add(new JLabel("Precio máximo:"));
        maxPriceField = new JTextField(10);
        leftInputPanel.add(maxPriceField);

        // Botón para agregar libro
        JButton addButton = new JButton("Agregar Libro");
        addButton.addActionListener(e -> addBook());
        leftInputPanel.add(addButton);

        // Campo de búsqueda
        leftInputPanel.add(new JLabel("Buscar libro:"));
        searchField = new JTextField(10);
        leftInputPanel.add(searchField);

        // Botón para buscar
        searchButton = new JButton("Buscar");
        searchButton.addActionListener(e -> searchBook());
        leftInputPanel.add(searchButton);

        // Botón para mostrar todas las subastas
        showAllButton = new JButton("Mostrar Todas las Subastas");
        showAllButton.setVisible(false); // Ocultar el botón al inicio
        showAllButton.addActionListener(e -> {
            showAllAuctions();
            showAllButton.setVisible(false); // Ocultar el botón cuando se presiona
        });
        leftInputPanel.add(showAllButton);

        // Botón para mostrar solo subastas de libros de interés
        showInterestedButton = new JButton("Mostrar Libros de Interés");
        showInterestedButton.addActionListener(e -> showInterestedAuctions());
        leftInputPanel.add(showInterestedButton);

        // Botones para entrar y salir
        enterButton = new JButton("Entrar");
        exitButton = new JButton("Salir");
        exitButton.setVisible(true); // Visible por defecto
        exitButton.addActionListener(e -> {

            if (comprador.puedeSalir) { // Verificar el flag para proceder con la salida
                comprador.desRegistro(); // Llamar a la función desregistro, que va a llamar a comportamiento
                exitButton.setVisible(false); // Ocultar el botón "Salir"
                enterButton.setVisible(true); // Mostrar el botón "Entrar"
                agregarNotificacion("Estás saliendo de la sala de subastas.");
            } else {
                agregarNotificacion("No puedes salir de la sala aún. Has sido el ganador de la ultima ronda de una subasta que no ha terminado");
            }
        });

        enterButton.setVisible(false); // Ocultar al inicio
        enterButton.addActionListener(e -> {
            comprador.registro();
            enterButton.setVisible(false); // Ocultar el botón "Entrar"
            exitButton.setVisible(true); // Mostrar el botón "Salir"
            agregarNotificacion("Has entrado en la sala de subastas.");
        });

        // Subpanel derecho para los botones "Entrar" y "Salir"
        JPanel rightButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT)); // Alineación a la derecha
        rightButtonPanel.add(enterButton);
        rightButtonPanel.add(exitButton);

        // Agregar subpaneles al inputPanel
        inputPanel.add(leftInputPanel, BorderLayout.CENTER); // Campos de texto y búsqueda a la izquierda
        inputPanel.add(rightButtonPanel, BorderLayout.EAST); // Botones "Entrar" y "Salir" a la derecha

        // Agregar el panel inferior completo a la ventana
        add(inputPanel, BorderLayout.SOUTH);

        // Área de notificaciones
        areaMensajes = new JTextArea();
        areaMensajes.setEditable(false); // Solo lectura
        JScrollPane scrollMensajes = new JScrollPane(areaMensajes);
        scrollMensajes.setBorder(BorderFactory.createTitledBorder("Notificaciones"));
        scrollMensajes.setPreferredSize(new Dimension(800, 100));

        // Agregar el área de mensajes en la parte superior de la ventana
        add(scrollMensajes, BorderLayout.NORTH);
    }


    public void showBookInputDialog() {
        setVisible(true);
    }

    public String configureAndSetPhoneNumber() {
        JTextField phoneNumberField = new JTextField();
        Object[] message = {
                "Ingrese su número de teléfono:", phoneNumberField
        };

        int option = JOptionPane.showConfirmDialog(
                null, message, "Número de Teléfono", JOptionPane.OK_CANCEL_OPTION
        );

        if (option == JOptionPane.OK_OPTION) {
            String phoneNumber = phoneNumberField.getText().trim();

            // Validar el número ingresado
            if (!phoneNumber.isEmpty() && phoneNumber.matches("\\d+")) {
                return phoneNumber; // Retornar el número para confirmación en el agente
            } else {
                JOptionPane.showMessageDialog(
                        null,
                        "Por favor, ingrese un número de teléfono válido.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );

                // Reintentar automáticamente
                return configureAndSetPhoneNumber();
            }
        }
        // Cancelación
        return null;
    }


    public void addBook() {
        String bookTitle = bookTitleField.getText().trim();
        String maxPriceStr = maxPriceField.getText().trim();

        if (bookTitle.isEmpty() || maxPriceStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Por favor, complete todos los campos.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            double maxPrice = Double.parseDouble(maxPriceStr);
            this.comprador.anhadirLibro(bookTitle, maxPrice);

            // Agregar libro a la tabla de libros interesados
            interestedBooksModel.addRow(new Object[]{bookTitle, maxPrice});

            if (!bookPanels.containsKey(bookTitle)) {
                createNewBookPanel(bookTitle);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Precio no válido.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createNewBookPanel(String bookTitle) {
        JPanel bookPanel = new JPanel();
        bookPanel.setLayout(new BoxLayout(bookPanel, BoxLayout.Y_AXIS));
        bookPanel.setBorder(BorderFactory.createTitledBorder(bookTitle));

        // Crear la tabla sin la columna "Ganador"
        DefaultTableModel tableModel = new DefaultTableModel(
                new Object[]{"Libro", "Vendedor", "Precio Actual", "Pujadores", "Ronda"}, 0
        );
        JTable auctionTable = new JTable(tableModel);
        auctionTable.setEnabled(false); // Tabla no editable
        // Ajustar el ancho de las columnas
        auctionTable.getColumnModel().getColumn(0).setPreferredWidth(100); // Subasta
        auctionTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Vendedor
        auctionTable.getColumnModel().getColumn(2).setPreferredWidth(70); // Precio Actual
        auctionTable.getColumnModel().getColumn(3).setPreferredWidth(200); // Pujadores
        auctionTable.getColumnModel().getColumn(4).setPreferredWidth(50);  // Ronda


        bookPanel.add(new JScrollPane(auctionTable));

        auctionPanelContainer.add(bookPanel);
        bookPanels.put(bookTitle, bookPanel);

        auctionPanelContainer.revalidate();
        auctionPanelContainer.repaint();
    }

    public void updateAuctionStatus(String bookTitle, String sellerName, double currentPrice, String bidders, int roundNumber) {
        // Obtener el panel del libro. Si no existe, se crea uno nuevo.
        JPanel bookPanel = bookPanels.get(bookTitle);

        if (bookPanel == null) {
            // Si no existe el panel, creamos uno nuevo.
            createNewBookPanel(bookTitle);
            // Ahora buscamos el panel recién creado
            bookPanel = bookPanels.get(bookTitle);
        }

        // Obtener la tabla dentro del panel del libro.
        JTable auctionTable = (JTable) ((JScrollPane) bookPanel.getComponent(0)).getViewport().getView();
        DefaultTableModel model = (DefaultTableModel) auctionTable.getModel();

        // Agregar una nueva fila a la tabla con la información de la subasta.
        Object[] rowData = {bookTitle, sellerName, currentPrice, bidders, roundNumber};
        model.addRow(rowData);

        // Obtener el nombre del comprador
        String buyerName = this.comprador.getLocalName();

        // Buscar la fila que contiene al comprador y cambiar su color de fondo
        for (int row = 0; row < model.getRowCount(); row++) {
            String biddersList = (String) model.getValueAt(row, 3); // Columna de pujadores

            // Verificar si el nombre del comprador está en la lista de pujadores
            if(bidders.contains("Ganador")){
                if (biddersList.contains(buyerName)){
                    auctionTable.setSelectionBackground(Color.BLUE); // Establecer el color de la selección (amarillo claro)
                    auctionTable.setSelectionForeground(Color.BLACK); // Establecer el color del texto al seleccionarlo (opcional)
                    auctionTable.setBackground(Color.decode("#DDEEFF"));
                }else {

                    auctionTable.setSelectionBackground(Color.RED); // Establecer el color de la selección (amarillo claro)
                    auctionTable.setSelectionForeground(Color.BLACK); // Establecer el color del texto al seleccionarlo (opcional)
                    auctionTable.setBackground(Color.decode("#FFDDDD"));
                }
            }
            else if (biddersList.contains(buyerName)) {
                // Cambiar el color de fondo de la fila a amarillo claro
                auctionTable.setSelectionBackground(Color.YELLOW); // Establecer el color de la selección (amarillo claro)
                auctionTable.setSelectionForeground(Color.BLACK); // Establecer el color del texto al seleccionarlo (opcional)
                auctionTable.setBackground(Color.decode("#FFFF99")); // Establecer el color de fondo de la fila (amarillo claro)
                break; // Salir del bucle después de encontrar al comprador
            }

        }

        // Actualizar la vista de los paneles
        auctionPanelContainer.revalidate();
        auctionPanelContainer.repaint();
    }



    public void markBookAsPurchased(String bookTitle, double finalPrice) {
        // Eliminar el libro de la tabla de interesados
        for (int i = 0; i < interestedBooksModel.getRowCount(); i++) {
            if (interestedBooksModel.getValueAt(i, 0).equals(bookTitle)) {
                // Obtener el precio como Double
                Object priceObject = interestedBooksModel.getValueAt(i, 1);
                if (priceObject instanceof Number) {
                    double maxPrice = ((Number) priceObject).doubleValue();
                    if (maxPrice >= finalPrice) {
                        interestedBooksModel.removeRow(i);
                        break; // Salir del bucle una vez encontrado
                    }
                }

            }
        }
        // Agregar el libro a la tabla de comprados
        purchasedBooksModel.addRow(new Object[]{bookTitle, finalPrice});
    }

    // Método para buscar un libro y hacer visible la subasta
    private void searchBook() {
        String searchQuery = searchField.getText().trim();

        if (searchQuery.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Por favor, ingrese un título de libro para buscar.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Limpiar el contenedor de subastas
        auctionPanelContainer.removeAll();

        // Buscar el panel correspondiente al libro
        JPanel bookPanel = bookPanels.get(searchQuery);

        if (bookPanel != null) {
            // Si el panel existe, hacerlo visible
            auctionPanelContainer.add(bookPanel); // Añadir solo el panel del libro encontrado
            showAllButton.setVisible(true); // Mostrar el botón
        } else {
            JOptionPane.showMessageDialog(this, "No se encontró el libro con ese título.", "Resultado de búsqueda", JOptionPane.INFORMATION_MESSAGE);
            showAllButton.setVisible(false); // Ocultar el botón si no se encuentra el libro
        }

        auctionPanelContainer.revalidate();
        auctionPanelContainer.repaint();
    }


    private void showInterestedAuctions() {
        auctionPanelContainer.removeAll();

        for (int i = 0; i < interestedBooksModel.getRowCount(); i++) {
            String bookTitle = (String) interestedBooksModel.getValueAt(i, 0);
            JPanel bookPanel = bookPanels.get(bookTitle);

            if (bookPanel != null) {
                auctionPanelContainer.add(bookPanel);
            }
        }

        auctionPanelContainer.revalidate();
        auctionPanelContainer.repaint();

        showAllButton.setVisible(true); // Mostrar el botón para regresar
        showInterestedButton.setVisible(false); // Ocultar el botón de interés
    }

    private void showAllAuctions() {
        auctionPanelContainer.removeAll();

        for (JPanel panel : bookPanels.values()) {
            auctionPanelContainer.add(panel);
        }

        auctionPanelContainer.revalidate();
        auctionPanelContainer.repaint();

        showAllButton.setVisible(false); // Ocultar este botón
        showInterestedButton.setVisible(true); // Mostrar el botón de libros de interés
    }



    public void agregarNotificacion(String mensaje) {
        // Añadir el mensaje al área de mensajes
        areaMensajes.append(mensaje + "\n");
        // Asegurarse de que el último mensaje sea visible
        areaMensajes.setCaretPosition(areaMensajes.getDocument().getLength());
    }

    private void handleExitAction() {
            if(comprador.puedeSalir) {
                comprador.notificarSalida();
                dispose(); // Libera los recursos y cierra la ventana
            }else{
                // Mostrar ventana de error con un mensaje
                JOptionPane.showMessageDialog(
                        this,
                        "No puedes salir en este momento. Has sido el ganador de la última ronda de una subasta en curso.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
    }



}
