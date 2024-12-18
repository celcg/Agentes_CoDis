package codigo;

import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;

import java.sql.SQLOutput;
import java.util.*;

import ontologia.*;
import jade.content.lang.Codec;
import ontologia.impl.*;

public class Vendedor extends Agent {
    // Mapa para manejar múltiples subastas: título del libro -> información de la subasta
    private Map<String, Subasta> mapaSubastas;
    private VendedorGUI gui;
    private Codec codec;
    private  Ontology ontology;

    @Override
    protected void setup() {
        System.out.println(getLocalName() + ": Inicializando vendedor...");

        //Registrar lenguaje y ontologia
        codec = new SLCodec();
        ontology = IbrowOntology.getInstance();
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);
        // Configuración inicial de subastas
        mapaSubastas = new HashMap<>();
        // Crear la GUI del vendedor y hacerla visible
        gui = new VendedorGUI(this);
        gui.setVisible(true);
        gui.agregarNotificacion("Agente vendedor iniciado. Configura las subastas usando la interfaz.");

        // Comportamiento para recibir respuestas
        addBehaviour(new ReceiveResponsesBehaviour());
        addBehaviour(new ReceiveBuyerResponseBehaviour());
        addBehaviour(new ReceiveNotUnderstoodBehaviour());
        addBehaviour(new ReceiveRefuseBehaviour());
    }

    /**
     * Clase interna para representar una subasta.
     */
    private class Subasta {
        // Datos de la subasta
        String bookTitle;  // Título del libro
        float currentPrice;  // Precio actual de la subasta
        float startingPrice;  // Precio actual de la subasta
        float increment;  // Incremento del precio
        Set<String> participants;  // Lista de participantes en la subasta
        Set<String> pujadores;  // Lista de participantes en la subasta
        int pujas=0;
        int ronda=0;
        boolean compraConfirmada=false;
        String lastBidder=null;  // El último postor (quien ha pujado en la ronda actual)
        String previousBidder=null;  // El postor de la ronda anterior (quien pujó antes de la última ronda)
        String buyer;

        // Constructor de la subasta
        public Subasta(String bookTitle, float startingPrice, float increment) {
            this.bookTitle = bookTitle;
            this.startingPrice=startingPrice;
            this.currentPrice = startingPrice;
            this.increment = increment;
            this.participants = new HashSet<>();
            this.pujadores = new HashSet<>();
            this.lastBidder = null;
            this.previousBidder = null;
        }

        // Resetear para una nueva ronda de subasta
        public void resetForNewRound() {
            this.previousBidder = this.lastBidder;  // Guardar el último postor de la ronda anterior
            this.lastBidder = null;  // Limpiar el último postor para la nueva ronda
            this.pujas=0;
            this.pujadores=new HashSet<>();
            if(ronda!=0)
                this.currentPrice+=increment;
            this.ronda++;
        }
    }

    /**
     * Comportamiento principal que maneja las rondas de una subasta específica.
     */
    private class AuctionBehaviour extends TickerBehaviour {
        private final Subasta subasta;

        public AuctionBehaviour(Agent a, Subasta subasta, long period) {
            super(a, period);
            this.subasta = subasta;
        }

        @Override
        protected void onTick() {
            boolean continuar=true;
            if (subasta.ronda != 0) {
                if (subasta.pujas == 0) {  // Nadie pujó en la ronda anterior
                    // Si no hubo pujas, el ganador es el previousBidder
                    if (subasta.previousBidder != null) {
                        subasta.buyer= subasta.previousBidder;
                        gui.agregarNotificacion("Subasta '" + subasta.bookTitle + "': Nadie pujó en la ronda anterior. Ganador: " + subasta.previousBidder);
                        subasta.currentPrice -= subasta.increment;
                        informResult(subasta, subasta.buyer);

                        continuar = false;

                        // Actualizar la tabla de subastas activas y finalizadas
                        gui.actualizarSubastaEnTabla(subasta.bookTitle, subasta.currentPrice, subasta.increment,"Ganador: " + subasta.previousBidder, subasta.ronda);

                    } else { // Caso de la ronda 1, cuando nadie haya pujado
                        subasta.ronda = 0;
                        // Aquí actualizamos la tabla de subastas activas para reflejar que no hubo pujas
                        gui.actualizarSubastaEnTabla(subasta.bookTitle, subasta.currentPrice, subasta.increment, "Sin pujas", subasta.ronda);
                    }
                } else if (subasta.pujas == 1) {  // Solo una puja en la ronda anterior
                    // El ganador es el lastBidder
                    gui.agregarNotificacion("Subasta '" + subasta.bookTitle + "': Solo una puja en la ronda anterior. Ganador: " + subasta.lastBidder);
                    informResult(subasta, subasta.lastBidder);
                    subasta.buyer= subasta.lastBidder;
                    continuar = false;

                    // Actualizar la tabla de subastas activas y finalizadas
                    gui.actualizarSubastaEnTabla(subasta.bookTitle, subasta.currentPrice, subasta.increment,"Ganador: " + subasta.lastBidder, subasta.ronda);
                } else {  // Más de una puja en la ronda anterior
                    // Enviar un acceptProposal a lastBidder para indicarle que no abandone la subasta
                    gui.agregarNotificacion("Subasta '" + subasta.bookTitle + "': Más de una puja en la ronda anterior.");
                    informRoundResults(subasta);

                    // Actualizar la tabla de subastas activas con los nuevos detalles de la subasta
                    gui.actualizarSubastaEnTabla(subasta.bookTitle, subasta.currentPrice, subasta.increment,String.join(", ", subasta.pujadores), subasta.ronda);
                }
            }

            if(!continuar){
                gui.agregarNotificacion("Subasta finalizada para '" + subasta.bookTitle + "'.");
                myAgent.removeBehaviour(this); // Elimina el comportamiento
                return; // Salir del action
            }
            // Pasar a la siguiente ronda
            subasta.resetForNewRound();
            // Consultar las Páginas Amarillas para localizar compradores
            updateParticipants(subasta);
            // Si no hay participantes
            if (subasta.participants.isEmpty()) {
                if (subasta.ronda == 1) {  // Si es la primera ronda se espera
                    subasta.ronda=0;
                    // Aquí actualizamos el estado de la subasta en la tabla de activas
                    gui.actualizarSubastaEnTabla(subasta.bookTitle, subasta.currentPrice, subasta.increment, "Esperando compradores", subasta.ronda);
                    return;  // No hacer nada hasta que haya participantes
                } else {
                    // Si no es la primera ronda, se reinicia la subasta
                    subasta.previousBidder = null;
                    subasta.lastBidder = null;
                    subasta.currentPrice = subasta.startingPrice;
                    subasta.ronda = 0;

                    // Aquí actualizamos la tabla de subastas activas para reflejar el reinicio
                    gui.actualizarSubastaEnTabla(subasta.bookTitle, subasta.currentPrice, subasta.increment, "Reiniciada", subasta.ronda);
                    return;
                }
            }
            sendCallForProposal(subasta);

        }
    }

    /**
     * Actualizar la lista de compradores interesados para una subasta.
     */
    private void updateParticipants(Subasta subasta) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("auction-participant");
        template.addServices(sd);

        try {
            DFAgentDescription[] results = DFService.search(this, template);
            Set<String> newParticipants = new HashSet<>();  // Conjunto para los nuevos participantes

            // Iterar sobre los resultados encontrados
            for (DFAgentDescription result : results) {
                String participant = result.getName().getLocalName();

               if(!(subasta.participants.contains(participant))){
                            informStart(subasta, participant);
                        }
                newParticipants.add(participant);

            }
            // Añadir los nuevos participantes a la lista de la subasta
            subasta.participants=newParticipants;
            //Informar a los nuevos participantes de la subasta
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }


    /**
     * Enviar mensajes de tipo CFP a los compradores interesados en una subasta.
     */
    private void sendCallForProposal(Subasta subasta) {
        DefaultOfertar ofertarAction = new DefaultOfertar();
        DefaultOferta oferta = new DefaultOferta();
        DefaultLibro libro=new DefaultLibro();
        libro.setPrecio(subasta.currentPrice);
        libro.setTitulo(subasta.bookTitle);
        oferta.setProducto(libro);
        ofertarAction.setOferta(oferta);

        // Crear un mensaje CFP (Call For Proposal)
        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
        cfp.setLanguage(codec.getName()); // Usar el codec (p. ej., SL)
        cfp.setOntology(ontology.getName()); // Usar la ontología registrada

        // Agregar los receptores
        for (String participant : subasta.participants) {
            cfp.addReceiver(new jade.core.AID(participant, jade.core.AID.ISLOCALNAME));
        }
        // Envolver el objeto `Ofertar` en una acción y establecerla como contenido del mensaje
        Action action = new Action(getAID(), ofertarAction);
        try {
            getContentManager().fillContent(cfp, action);
        } catch (Codec.CodecException | OntologyException e) {
            throw new RuntimeException(e);
        }
        // Generar un identificador único para el mensaje
        String uniqueId = "cfp" + System.currentTimeMillis();
        cfp.setReplyWith(uniqueId);
        send(cfp);
    }

    /**
     * Recibir y procesar respuestas de los compradores.
     */
    private class ReceiveResponsesBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            String[] content;
            String bookTitle = null;
            boolean respuesta = false;
            Subasta subasta = null;
            String id;

            if (msg != null) {
                // Verificar si el mensaje es del tipo PROPOSE
                if (msg.getPerformative() == ACLMessage.PROPOSE) {
                    id=msg.getReplyWith();
                    try {
                        // Extraer el contenido del mensaje usando el gestor de contenido
                        Action a = (Action) getContentManager().extractContent(msg);
                        DefaultProponer propuesta = (DefaultProponer) a.getAction();
                        Libro libro=propuesta.getLibro();
                        bookTitle=libro.getTitulo();
                        respuesta=propuesta.getRespuesta();
                        // Validar el contenido del mensaje
                        if (mapaSubastas.get(bookTitle) == null) {
                            throw new Exception("No se encuentra el libro en las subastas activas.");
                        }
                        subasta = mapaSubastas.get(bookTitle);  // Buscar la subasta correspondiente

                    } catch (Exception e) {
                            ACLMessage reply = msg.createReply();
                            reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                            reply.setContent("No se ha entendido un mensaje inform");
                    }
                    // Si la subasta existe, procesar la respuesta
                    if (subasta != null) {
                        if (respuesta) {
                            subasta.pujas++;
                            subasta.pujadores.add(msg.getSender().getLocalName());
                            // Si es la primera vez que alguien puja, actualizamos previousBidder
                            if (subasta.lastBidder == null) {
                                subasta.previousBidder = msg.getSender().getLocalName();  // Registrar al primer postor
                                sendAcceptProposal(subasta, subasta.previousBidder, id);
                            } else {
                                sendRejectProposal(subasta, msg.getSender().getLocalName(), id);
                            }
                            // Actualizar el último postor
                            subasta.lastBidder = msg.getSender().getLocalName();
                        } else {
                            sendRejectProposal(subasta, msg.getSender().getLocalName(), id);
                        }
                    }
                } else {
                    block();
                }
            } else {
                block();
            }
        }
    }


    /**
     * Informar a los participantes del resultado de una subasta.
     */
    private void informResult(Subasta subasta, String winner) {

        // Crear un mensaje Inform
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        inform.setLanguage(codec.getName());
        inform.setOntology(ontology.getName());
        // Agregar los receptores
        for (String participant : subasta.participants) {
            inform.addReceiver(new jade.core.AID(participant, jade.core.AID.ISLOCALNAME));
        }

        DefaultInformarFinal informarFinal = new DefaultInformarFinal();
        informarFinal.setGanador(winner);
        DefaultLibro libro= new DefaultLibro();
        libro.setPrecio(subasta.currentPrice);
        libro.setTitulo(subasta.bookTitle);
        informarFinal.setLibro(libro);
        informarFinal.setNumeroRonda(subasta.ronda);
        Action action = new Action(getAID(), informarFinal);
        try {
            getContentManager().fillContent(inform, action);
        } catch (Codec.CodecException | OntologyException e) {
            throw new RuntimeException(e);
        }
        // Generar un identificador único para el mensaje
        String uniqueId = "informResult_" + System.currentTimeMillis();
        inform.setReplyWith(uniqueId);

        send(inform);
        sendPurchaseConfirmationRequest(subasta,winner);
        addBehaviour(new AuctionTimeoutBehaviour(this,30000, subasta));
    }

    private class AuctionTimeoutBehaviour extends WakerBehaviour {
        private final Subasta subasta; // Instancia de la subasta para reiniciar en caso de timeout

        public AuctionTimeoutBehaviour(Agent agent, long timeout, Subasta subasta) {
            super(agent, timeout);
            this.subasta = subasta; // Guardar la referencia de la subasta
        }

        @Override
        protected void onWake() {
            if (!subasta.compraConfirmada) { // Verifica si la confirmación no fue recibida
                gui.agregarNotificacion( " No se recibió una confirmación de compra en 20 segundos de "+ subasta.bookTitle +". Reiniciando la subasta...");
                reiniciarSubasta();
            }
        }

        /**
         * Lógica para reiniciar la subasta.
         */
        private void reiniciarSubasta() {
            //System.out.println("Reiniciando la subasta para el libro: " + subasta.bookTitle);
            mapaSubastas.remove(subasta.bookTitle);
             iniciarSubasta(subasta.bookTitle, subasta.currentPrice, subasta.increment);
        }
    }

    private void sendPurchaseConfirmationRequest(Subasta subasta, String winner) {
        // Crear el AID del comprador a partir del nombre (suponemos que el nombre es único)
        jade.core.AID buyerAID = new jade.core.AID(winner, jade.core.AID.ISLOCALNAME);
        // Crear el mensaje de solicitud de compra
        ACLMessage requestMessage = new ACLMessage(ACLMessage.REQUEST);
        requestMessage.addReceiver(buyerAID);  // Añadir el comprador como receptor
        requestMessage.setLanguage(codec.getName());
        requestMessage.setOntology(ontology.getName());
        String uniqueId = "requestCompra_" + System.currentTimeMillis();
        requestMessage.setReplyWith(uniqueId);

        // Crear la accion de InformarCompra
        DefaultLibro libro = new DefaultLibro();
        libro.setTitulo(subasta.bookTitle);
        libro.setPrecio(subasta.currentPrice);
        enviarMensajeDeCompra(requestMessage, libro);
    }

    private void enviarMensajeDeCompra(ACLMessage requestMessage, DefaultLibro libro) {
        DefaultInformarCompra informarCompra = new DefaultInformarCompra();
        informarCompra.setLibro(libro);
        Action action = new Action(getAID(), informarCompra);
        try {
            getContentManager().fillContent(requestMessage, action);
        } catch (Codec.CodecException | OntologyException e) {
            throw new RuntimeException(e);
        }
        send(requestMessage);
    }


    /**
     * Informar a los participantes del inicio de una subasta.
     */
    private void informStart(Subasta subasta, String participant) {
        // Crear un mensaje Inform
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        inform.setLanguage(codec.getName());
        inform.setOntology(ontology.getName());
        // Agregar los receptores
        inform.addReceiver(new jade.core.AID(participant, jade.core.AID.ISLOCALNAME));

        // Crear la accion de InformarNuevaSubasta
        DefaultLibro libro = new DefaultLibro();
        libro.setTitulo(subasta.bookTitle);
        libro.setPrecio(subasta.currentPrice);
        DefaultInformarNuevaSubasta informarNuevaSubasta = new DefaultInformarNuevaSubasta();
        informarNuevaSubasta.setLibro(libro);
        informarNuevaSubasta.setIncremento(subasta.increment);

        Action action = new Action(getAID(), informarNuevaSubasta);
        try {
            getContentManager().fillContent(inform, action);
        } catch (Codec.CodecException | OntologyException e) {
            throw new RuntimeException(e);
        }
        // Generar un identificador único para el mensaje
        String uniqueId = "informNuevaSubasta_" + System.currentTimeMillis();
        inform.setReplyWith(uniqueId);
        send(inform);
        /*
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        String message = "Subasta iniciada para '" + subasta.bookTitle +
                "'. Precio: " + subasta.currentPrice +
                ". Incremento: " + subasta.increment + ".";
        inform.setContent(message);
        inform.addReceiver(new jade.core.AID(participant, jade.core.AID.ISLOCALNAME));

        send(inform);*/
    }

    private void informRoundResults(Subasta subasta) {
        // Crear el mensaje de información
        ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
        inform.setLanguage(codec.getName());
        inform.setOntology(ontology.getName());

        DefaultInformarRonda informarRonda = getDefaultInformarRonda(subasta);
        // Enviar el mensaje a todos los participantes
        for (String participant : subasta.participants) {
            inform.addReceiver(new jade.core.AID(participant, jade.core.AID.ISLOCALNAME));
        }
        Action action = new Action(getAID(), informarRonda);
        try {
            getContentManager().fillContent(inform, action);
        } catch (Codec.CodecException | OntologyException e) {
            throw new RuntimeException(e);
        }
        // Generar un identificador único para el mensaje
        String uniqueId = "informRonda_" + System.currentTimeMillis();
        inform.setReplyWith(uniqueId);

        send(inform);
    }

    private static DefaultInformarRonda getDefaultInformarRonda(Subasta subasta) {
        DefaultRondaSubasta infoRonda = new DefaultRondaSubasta();
        infoRonda.setIncremento(subasta.increment);
        infoRonda.setNumeroRonda(subasta.ronda);

        jade.util.leap.List pujadoresLeap = new jade.util.leap.ArrayList();
        for (String pujador : subasta.pujadores) {
            pujadoresLeap.add(pujador);
        }
        infoRonda.setPujadores(pujadoresLeap);

        DefaultLibro libro = new DefaultLibro();
        libro.setPrecio(subasta.currentPrice);
        libro.setTitulo(subasta.bookTitle);
        infoRonda.setLibro(libro);
        DefaultInformarRonda informarRonda = new DefaultInformarRonda();
        informarRonda.setRondaSubasta(infoRonda);
        return informarRonda;
    }

    private void sendAcceptProposal(Subasta subasta, String bidder, String id) {
        ACLMessage acceptProposal = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
        generarRepuestaAPropuesta(subasta, id, acceptProposal);
        // Generar un identificador único para el mensaje
        String uniqueId = "accept_" + System.currentTimeMillis();
        acceptProposal.setReplyWith(uniqueId);
        acceptProposal.addReceiver(new jade.core.AID(bidder, jade.core.AID.ISLOCALNAME));
        // Enviar el mensaje de rechazo
        send(acceptProposal);
    }

    private void generarRepuestaAPropuesta(Subasta subasta, String id, ACLMessage acceptProposal) {
        acceptProposal.setInReplyTo(id);
        acceptProposal.setLanguage(codec.getName());
        acceptProposal.setOntology(ontology.getName());

        DefaultResponderPropuesta responderPropuesta = new DefaultResponderPropuesta();
        Libro libro = new DefaultLibro();
        libro.setTitulo(subasta.bookTitle);
        libro.setPrecio(subasta.currentPrice);
        responderPropuesta.setLibro(libro);
        Action action = new Action(getAID(), responderPropuesta);
        try {
            getContentManager().fillContent(acceptProposal, action);
        } catch (Codec.CodecException | OntologyException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendRejectProposal(Subasta subasta, String bidder, String id) {
        ACLMessage rejectProposal = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
        generarRepuestaAPropuesta(subasta, id, rejectProposal);
        // Generar un identificador único para el mensaje
        String uniqueId = "reject_" + System.currentTimeMillis();
        rejectProposal.setReplyWith(uniqueId);
        rejectProposal.addReceiver(new jade.core.AID(bidder, jade.core.AID.ISLOCALNAME));
        // Enviar el mensaje de rechazo
        send(rejectProposal);
    }

    public void iniciarSubasta(String titulo, float precioInicial, float incremento) {
        if (!mapaSubastas.containsKey(titulo)) {
            Subasta subasta = new Subasta(titulo, precioInicial, incremento);
            mapaSubastas.put(titulo, subasta);
            addBehaviour(new AuctionBehaviour(this, subasta, 10000)); // Intervalo de 10 segundos entre rondas
            gui.agregarNotificacion("Subasta iniciada para '" + titulo + "' con precio inicial " + precioInicial + " y incremento " + incremento + ".");
        } else {
            gui.agregarNotificacion("Ya existe una subasta para el libro '" + titulo + "'.");
        }
    }

    private class ReceiveBuyerResponseBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            Subasta subasta;
            float precio;
            int telefono;
            String booktitle;
            // Plantilla para recibir AGREE
            MessageTemplate agreeTemplate = MessageTemplate.MatchPerformative(ACLMessage.AGREE);
            ACLMessage msg = receive(agreeTemplate);

            if (msg != null) {
                //System.out.println(("agree recibido"));
                String buyerName = msg.getSender().getLocalName();
                try {
                    // Extraer el contenido del mensaje utilizando el ContentManager
                    Action action = (Action)  getContentManager().extractContent(msg);
                    DefaultMandarDatosCompra  infoCompra= (DefaultMandarDatosCompra) action.getAction();
                    booktitle= infoCompra.getLibro().getTitulo();
                    precio=infoCompra.getLibro().getPrecio();
                    telefono= infoCompra.getTelefono();
                    subasta = mapaSubastas.get(booktitle);
                    if((booktitle==null)||(subasta==null))
                        throw new Exception();
                } catch (Exception e) {
                  //  System.out.println("excep");
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                    send(reply);
                    return;
                }
                 if (!subasta.buyer.equals(buyerName)) {
                    // System.out.println("PRoblem");
                        return;
                 } else if (Math.abs(precio - subasta.currentPrice) > 1.e-6) {
                     System.out.println(precio);
                     System.out.println(subasta.currentPrice);
                        sendPurchaseConfirmationRequest(subasta, buyerName);
                } else {
                        // Registrar la venta en el sistema
                        subasta.compraConfirmada = true;
                        mapaSubastas.remove(booktitle);
                        gui.notificarLibroVendido(booktitle, buyerName, precio, String.valueOf(telefono));
                        gui.agregarNotificacion("Venta registrada. Libro: " + booktitle +
                                ", codigo.Comprador: " + buyerName + ", Precio: " + precio +
                                    ", Teléfono: " + telefono);

                            //Enviar mensaje inform de que se ha confirmado la compra
                            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                            inform.setLanguage(codec.getName());
                            inform.setOntology(ontology.getName());
                            inform.addReceiver(new jade.core.AID(buyerName, jade.core.AID.ISLOCALNAME));
                            String uniqueId = "informCompra_" + System.currentTimeMillis();
                            inform.setReplyWith(uniqueId);

                            // Crear la accion de InformarCompra
                            DefaultLibro libro = new DefaultLibro();
                            libro.setTitulo(booktitle);
                            libro.setPrecio(precio);
                            enviarMensajeDeCompra(inform, libro);
                    }
                 block();
            } else {
                block();
            }

        }
    }

    protected void notificarSalida(){
        this.doDelete();
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

    private class ReceiveRefuseBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            // Plantilla para filtrar mensajes de tipo REFUSE
            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.REFUSE);
            ACLMessage msg = receive(template); // Recibir mensaje que coincida con la plantilla

            if (msg != null) {
                try {
                    // Extraer el contenido del mensaje utilizando el ContentManager
                    Action action = (Action)  getContentManager().extractContent(msg);
                    DefaultInformarCompra  infoCompra= (DefaultInformarCompra) action.getAction();
                    String booktitle= infoCompra.getLibro().getTitulo();
                    String senderName = msg.getSender().getLocalName(); // Obtiene el nombre local (sin el sufijo del contenedor)
                    gui.agregarNotificacion("El comprador "+senderName+ " ha rechazado la compra de "+booktitle+". Se reiniciará la subasta si no la acepta...");
                } catch (Exception e) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                    send(reply);
                }
            } else {
                block(); // Bloquear el comportamiento si no hay mensajes que procesar
            }
        }
    }

}
