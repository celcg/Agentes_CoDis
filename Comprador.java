package codigo;

import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import ontologia.IbrowOntology;
import ontologia.Libro;
import ontologia.RondaSubasta;
import ontologia.impl.*;



import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Comprador extends Agent {
    private final ArrayList<DefaultLibro> bookPreferences = new ArrayList<>();
    private CompradorGUI gui;
    private int telefono;
    protected boolean puedeSalir = true;
    private final Set<String> librosGanadorRonda = new HashSet<>(); //libros de los que ha sido ganador en la ronda anterior
    private final Set<String> librosEsperandoConfirmaciones = new HashSet<>();
    private Codec codec;
    private Ontology ontology;

    @Override
    protected void setup() {
        System.out.println(getLocalName() + ": Inicializando comprador...");

        //Ontologia y lenguaje
        codec = new SLCodec();
        ontology = IbrowOntology.getInstance();
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);

        // Inicializar la GUI
        gui = new CompradorGUI(this);

        // Leer las preferencias del usuario
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                configurePreferences();
            }
        });
        // Registrar al agente en las Páginas Amarillas
        registerInYellowPages();
        // Comportamiento para gestionar propuestas.
        addBehaviour(new ReceiveCFProposalBehaviour());
        // Comportamiento para recibir resultados de la subasta.
        addBehaviour(new ReceiveInformsBehaviour());
        // Comportamiento para recibir si las propuestas son aceptadas o rechazadas.
        addBehaviour(new ReceiveProposalResponseBehaviour());
        //Comportamiento para recibir Request, que indican que eres el ganador
        addBehaviour(new ReceiveRequestBehaviour());
        //Recibir Not understood
        addBehaviour(new ReceiveNotUnderstoodBehaviour());
    }

    private void configurePreferences() {
        SwingUtilities.invokeLater(() -> {
            // Delegar la lógica completa a la GUI
            String phoneNumber = gui.configureAndSetPhoneNumber();

            if (phoneNumber != null) {
                this.telefono= Integer.parseInt(phoneNumber);
                gui.agregarNotificacion("Número de teléfono configurado: " + phoneNumber);
                gui.showBookInputDialog(); // Mostrar la ventana de libros después
            } else {
                gui.agregarNotificacion("Configuración cancelada o número inválido.");
                this.doDelete();
                gui.dispose();
            }
        });
    }

    protected void desRegistro(){
        addBehaviour(new SalirBehaviour());
    }
    protected void registro(){
        addBehaviour(new EntrarBehaviour());
    }


    protected void registerInYellowPages() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("auction-participant");
        sd.setName("JADE-English-Auction");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + ": Registrado en las Páginas Amarillas.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private class ReceiveCFProposalBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // Crear una plantilla para filtrar mensajes CFP (Call for Proposal)
            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = receive(template); // Recibir mensaje que coincida con la plantilla

            if (msg != null) {
                // Crear un mensaje de respuesta basado en el mensaje recibido
                ACLMessage reply = msg.createReply();
                reply.setOntology(ontology.getName());
                reply.setLanguage(codec.getName());
                reply.setReplyWith("propose_" + System.currentTimeMillis());
                try {
                    // Extraer el contenido del mensaje usando el gestor de contenido
                    Action a = (Action) getContentManager().extractContent(msg);
                    DefaultOfertar ofertar = (DefaultOfertar) a.getAction();
                    DefaultOferta oferta = (DefaultOferta) ofertar.getOferta();
                    DefaultLibro libro = (DefaultLibro) oferta.getProducto();

                    // Obtener detalles del libro ofertado
                    String titulo = libro.getTitulo();
                    float precio = libro.getPrecio();

                    // Crear un objeto de tipo DefaultProponer para la respuesta
                    DefaultProponer proponer = new DefaultProponer();
                    proponer.setLibro(libro); // Asignar el libro al objeto "proponer"
                    proponer.setRespuesta(shouldParticipate(titulo, precio));
                    reply.setPerformative(ACLMessage.PROPOSE); // Definir el tipo de mensaje como "PROPOSE"
                    // Rellenar el contenido del mensaje de respuesta con la acción "proponer"
                    Action action = new Action(getAID(), proponer);
                    getContentManager().fillContent(reply, action);
                } catch (Codec.CodecException | OntologyException e) {
                    // Manejar errores en caso de problemas con el contenido del mensaje
                    sendNotUnderstood(msg);
                    return;
                }
                send(reply);
            } else {
                block();
            }
        }
    }

    private class ReceiveInformsBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // Plantilla para recibir mensajes de tipo INFORM
            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = receive(template);

            if (msg != null) {
                try {
                    // Obtener el contenido del mensaje como un objeto
                    ContentElement content = getContentManager().extractContent(msg);
                    if (content instanceof Action) {
                        Action action = (Action) content;
                        Concept concept = action.getAction();
                        if (concept instanceof DefaultInformarNuevaSubasta) {
                            // Procesar inicio de subasta
                            DefaultInformarNuevaSubasta nuevaSubasta = (DefaultInformarNuevaSubasta) concept;
                            Libro libro = nuevaSubasta.getLibro();

                            String bookTitle = libro.getTitulo();
                            double startingPrice = libro.getPrecio();
                            double increment = nuevaSubasta.getIncremento();

                            gui.updateAuctionStatus(bookTitle, msg.getSender().getLocalName(), startingPrice, "Subasta Iniciada", 0);
                            for (DefaultLibro preferencia : bookPreferences) {
                                if (preferencia.getTitulo().equals(bookTitle)) {
                                    gui.agregarNotificacion("Subasta iniciada: '" + bookTitle + "', precio inicial: " + startingPrice + ", incremento: " + increment);
                                    break;
                                }
                            }
                        } else
                            if (concept instanceof DefaultInformarRonda informarRonda) {
                            // Procesar resultados de la ronda
                            RondaSubasta ronda = informarRonda.getRondaSubasta();

                            String bookTitle = ronda.getLibro().getTitulo();
                            double currentPrice = ronda.getLibro().getPrecio();
                            int rondaNumero = ronda.getNumeroRonda();

                            jade.util.leap.List pujadores = ronda.getPujadores();
                            String bidders = pujadores.toString();

                            gui.updateAuctionStatus(bookTitle, msg.getSender().getLocalName(), currentPrice, bidders, rondaNumero);
                        } else if (concept instanceof DefaultInformarCompra informarCompra) {
                            // Procesar confirmación de compra
                            Libro libro = informarCompra.getLibro();
                            String bookTitle = libro.getTitulo();
                            double finalPrice = libro.getPrecio();

                            if (librosEsperandoConfirmaciones.contains(bookTitle)) {
                                gui.agregarNotificacion("Comprado " + bookTitle + " al precio de " + finalPrice);
                                gui.markBookAsPurchased(bookTitle, finalPrice);
                                librosEsperandoConfirmaciones.remove(bookTitle);
                                librosGanadorRonda.remove(bookTitle);
                                if (librosGanadorRonda.isEmpty())
                                    puedeSalir = true;
                            }
                        } else if (concept instanceof DefaultInformarFinal informarFinal) {
                            // Procesar finalización de subasta
                            Libro libro = informarFinal.getLibro();
                            String bookTitle = libro.getTitulo();
                            double finalPrice = libro.getPrecio();
                            String winner = informarFinal.getGanador();
                            int ronda = informarFinal.getNumeroRonda();

                            gui.updateAuctionStatus(bookTitle, msg.getSender().getLocalName(), finalPrice, "Ganador: " + winner, ronda);
                            gui.agregarNotificacion("Subasta finalizada: '" + bookTitle + "', Ganador: " + winner + ", Precio final: " + finalPrice + ", Ronda: " + ronda);
                        } else {
                            throw new Exception("Concepto no reconocido en el mensaje INFORM");
                        }
                    }
                } catch (Exception e) {
                    sendNotUnderstood(msg);
                }
            } else {
                block();
            }
        }
    }

    private void sendNotUnderstood(ACLMessage msg) {
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
        String inreply= msg.getReplyWith();
        reply.setInReplyTo(inreply);
        reply.setContent("No se ha entendido el contenido del mensaje");
        send(reply);
    }

    private class ReceiveRequestBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            String bookTitle;
            float finalPrice;
            Libro libro;
            // Plantilla para recibir mensajes REQUEST
            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = receive(template);

            if (msg != null) {
                boolean denegado=true;
                ContentElement content = null;
                try {
                    content = getContentManager().extractContent(msg);
                    Action action = (Action) content;
                    Concept concept = action.getAction();
                    DefaultInformarCompra datosCompra = (DefaultInformarCompra) concept;
                    libro = datosCompra.getLibro();
                    bookTitle = libro.getTitulo();
                    finalPrice = libro.getPrecio();
                    if(bookTitle==null)
                        throw new Exception("No se ha encontrado el titulo");
                } catch (Exception e) {
                        sendNotUnderstood(msg);
                        return;
                }
                ACLMessage reply = msg.createReply();
                reply.setReplyWith("datosCompra_" + System.currentTimeMillis());
                reply.setOntology(ontology.getName());
                reply.setLanguage(codec.getName());
                // Actualizar la GUI con el estado de la compra confirmada
                Iterator<DefaultLibro> iterator = bookPreferences.iterator();
                while (iterator.hasNext()) {
                    DefaultLibro lib = iterator.next();
                    if (lib.getTitulo().equals(bookTitle)) {
                        if (lib.getPrecio() >= finalPrice) {
                            iterator.remove(); // Elimina el libro de forma segura
                            reply.setPerformative(ACLMessage.AGREE);
                            DefaultMandarDatosCompra mandarDatosCompra = new DefaultMandarDatosCompra();
                            mandarDatosCompra.setTelefono(telefono);
                            mandarDatosCompra.setLibro(libro);
                            Action action = new Action(getAID(), mandarDatosCompra);
                            try {
                                getContentManager().fillContent(reply, action);
                            } catch (Codec.CodecException | OntologyException e) {
                                throw new RuntimeException(e);
                            }
                           /* System.out.println("Mensaje AGREE enviado: " + reply);
                            System.out.println("Receptores: " + reply.getAllReceiver().next());
                            System.out.println("Contenido: " + reply.getContent());
*/
                            send(reply);
                            librosEsperandoConfirmaciones.add(bookTitle);
                            denegado = false;
                            break;
                        }
                    }
                }
                if(denegado){
                    // Enviar un REFUSE para denegar la compra
                    ACLMessage refuseReply = msg.createReply();
                    refuseReply.setPerformative(ACLMessage.REFUSE);
                    DefaultInformarCompra informarCompra = new DefaultInformarCompra();
                    informarCompra.setLibro(libro);
                    Action action = new Action(getAID(), informarCompra);
                    try {
                        getContentManager().fillContent(reply, action);
                    } catch (Codec.CodecException | OntologyException e) {
                        throw new RuntimeException(e);
                    }
                    send(refuseReply);
                }
            } else {
                block();
            }
        }
    }

    private class ReceiveProposalResponseBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            String booktitle;
            MessageTemplate template = MessageTemplate.or(
                    MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                    MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL)
            );
            ACLMessage msg = receive(template);

            if (msg != null) {
                ContentElement content = null;
                try {
                    content = getContentManager().extractContent(msg);
                    Action action = (Action) content;
                    Concept concept = action.getAction();
                    DefaultResponderPropuesta respuestaPropuesta = (DefaultResponderPropuesta) concept;
                    Libro libro = respuestaPropuesta.getLibro();
                    booktitle=libro.getTitulo();
                    boolean interes=false;
                    for(DefaultLibro lib: bookPreferences){
                        if(lib.getTitulo().equals(booktitle)){
                            interes=true;
                            break;
                        }
                    }
                    if(!interes)return;
                } catch (Exception e) {
                    sendNotUnderstood(msg);
                    return;
                }
                if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    librosGanadorRonda.add(booktitle);
                    puedeSalir=false;
                } else if (msg.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                    librosGanadorRonda.remove(booktitle);
                    if(librosGanadorRonda.isEmpty()){
                        puedeSalir=true;
                    }
                }
            } else {
                block();
            }
        }
    }

    private boolean shouldParticipate(String namebook, double price) {
        for(DefaultLibro libro: bookPreferences){
            if(libro.getTitulo().equals(namebook))
                if (libro.getPrecio() >= price)
                    return true;
        }
        return false;
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + ": desregistrado en las Páginas Amarillas.");
        gui.agregarNotificacion(" Terminando...");
    }

    protected void anhadirLibro(String nombreLibro, double price) {
        DefaultLibro libro= new DefaultLibro();
        libro.setPrecio((float) price);
        libro.setTitulo(nombreLibro);
        this.bookPreferences.add(libro);
    }

    protected void notificarSalida(){
        this.doDelete();
    }

    private class SalirBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            try {
                DFService.deregister(myAgent);
                gui.agregarNotificacion("Desregistrado de las Páginas Amarillas.");
            } catch (FIPAException e) {
                e.printStackTrace();
                gui.agregarNotificacion("Error al desregistrar de las Páginas Amarillas.");
            }
        }
    }

    private class EntrarBehaviour extends OneShotBehaviour {
        @Override
        public void action() {
            try {
                registerInYellowPages();
                gui.agregarNotificacion("Registro en las Páginas Amarillas completo.");
            } catch (Exception e) {
                e.printStackTrace();
                gui.agregarNotificacion("Error al registrar en las Páginas Amarillas.");
            }
        }
    }

    private class ReceiveNotUnderstoodBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.NOT_UNDERSTOOD);
            ACLMessage msg = receive(template);

            if (msg != null) {
                String sender = msg.getSender().getLocalName();
                gui.agregarNotificacion("Recibido NOT_UNDERSTOOD de: " + sender);
            } else {
                block();
            }
        }
    }



}
