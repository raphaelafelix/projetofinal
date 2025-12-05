import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class Servidor{

    private static Connection con; // Conexão com o Banco de Dados

    // Dando entrada no programa Java
    public static void main(String[] args) throws Exception {

        // Conectar ao SQLite (arquivo conteudo.db na pasta do projeto)
        con = DriverManager.getConnection("jdbc:sqlite:content.db");

        // Criar tabela (se não existir)
        String sql = "CREATE TABLE IF NOT EXISTS dados (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "materia TEXT," +
                "descricao TEXT," +
                "data TEXT," +
                "participacao TEXT" + // curtida
                ")";
        // Enviando ao Banco de Dados
        con.createStatement().execute(sql);

        // Criar servidor HTTP
        HttpServer s = HttpServer.create(new InetSocketAddress(8082), 0);

        // Rotas básicas
        s.createContext("/", t -> enviar(t, "login.html"));   // mostra login
        s.createContext("/login", Servidor::login);           // processa login
        s.createContext("/professor", Servidor::professor);     // publica e exclue atividades
        s.createContext("/aluno", Servidor::aluno); // visualiza e envia atividades
        s.createContext("/atividades", Servidor::atividades);
        s.createContext("/participar", Servidor::participar);       // curtir / não curtir
        s.createContext("/style.css", t -> enviarCSS(t, "style.css")); // CSS
        s.createContext("/global.css", t -> enviarCSS(t, "global.css")); // CSS
        s.createContext("/deletar", Servidor::deletar);



        s.start();
        System.out.println("Servidor rodando em http://localhost:8082/");
    }

    // -------------------- LOGIN --------------------

    private static void login(HttpExchange t) throws IOException {
        if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
            enviar(t, "login.html");
            return;
        } // Nesse comando, SE o método de requisição não for POST (enviar dados), o usuário será redirecionado para a página de Login

        String corpo = ler(t); // exemplo: tipo=produtor
        corpo = URLDecoder.decode(corpo, StandardCharsets.UTF_8);  // Aqui, a URL vai ser decodificada e convertida para leitura humana

        if (corpo.contains("professor")) {
            redirecionar(t, "/professor"); // Se o usuário selecionar a opção "Produtor", ele será direcionado para a página "Produtor"
        } else {
            redirecionar(t, "/aluno");
        } // Se o usuário selecionar outra opção, será redirecionado para a página "Consumidor"
    }

    // -------------------- PRODUTOR --------------------

    private static void professor(HttpExchange t) throws IOException {

        if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
            enviar(t, "professor.html");
            return;
        }

        String c = URLDecoder.decode(ler(t), StandardCharsets.UTF_8); // Os dados enviados ao banco de dados vão ser decodificados

        String nome = pega(c, "materia");
        String desc = pega(c, "descricao");
        String data = pega(c, "data");
        // Extrai os valores do banco de dados que foram decodificados e coloca eles em seus respectivos campos

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO dados (materia, descricao, data, participacao) VALUES (?,?,?,?)")) { // Há pontos de interrogação porque a ordem (ainda) é desconhecida

            ps.setString(1, nome);
            ps.setString(2, desc); // type: text
            ps.setString(3, data); // organizando os valores em seus respectivos parâmetros (type: data)
            ps.setString(4, "nenhuma"); // ainda não enviado
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace(); // Para escrever no leitor caso algo dê errado dentro
        }

        redirecionar(t, "/professor"); // Para ser redirecionado até a rota

    }


    //Deletar--------------------------------------------------------------------------

    private static void deletar(HttpExchange t) throws IOException {

        if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
            redirecionar(t, "/aluno");
            return;
        }

        String corpo = URLDecoder.decode(ler(t), StandardCharsets.UTF_8);
        String acao = pega(corpo, "acao"); //Participar ou não
        String idStr = pega(corpo, "id");

        try {
            int id = Integer.parseInt(idStr);

            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM dados WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        redirecionar(t, "/aluno");
    }
    // -------------------- CONSUMIDOR (lista todas as lições) --------------------

    private static void aluno(HttpExchange t) throws IOException {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>"); // Montando o esqueleto HTML para mostrar as curtidas
        html.append("<html><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<title>Aluno</title>");
        html.append("<link rel=\"stylesheet\" href=\"/style.css\">");
        html.append("</head><body>");
        html.append("<header class=\"cabecalho-topo\">");
        html.append("<h1>Bem-vindo(a) !</h1>");
        html.append("<a href=\"./login.html\" class=\"btn-voltar\">Voltar à página <img src=\"./src/assets/images/Login.svg\"></a>");
        html.append("</header>");
        html.append("<h1>Aluno</h1>");
        html.append("<p>As atividades disponíveis aparecem aqui:</p>");


        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, materia, descricao, data, participacao FROM dados ORDER BY id DESC")) { // para mostrar os dados obtidos

            boolean vazio = true; // Mostre os dados enquanto "vazio" realmente estiver vazio

            while (rs.next()) {
                vazio = false; // Enquanto não estiver vazio

                int id = rs.getInt("id"); // Extrata o valor da ID e mostre
                String nome = rs.getString("materia");
                String desc = rs.getString("descricao");
                String data = rs.getString("data");
                String participacao = rs.getString("participacao");

                // Classe extra para cor do card
                String classeExtra = "";
                if ("participacao".equals(participacao)) { // Se a postagem for curtida, mostre que o card foi curtido
                    classeExtra = "participar";
                } else if ("nao".equals(participacao)) {
                    classeExtra = "nao-participar";
                }

                html.append("<div class=\"card").append(classeExtra).append("\">");
                html.append("<p><strong>ID:</strong> ").append(id).append("</p>");
                html.append("<p><strong>Matéria:</strong> ").append(nome).append("</p>");
                html.append("<p><strong>Descrição:</strong> ").append(desc).append("</p>");
                html.append("<p><strong>Data:</strong> ").append(data).append("</p>");
                html.append("<p><strong>Participando:</strong> ").append(participacao).append("</p>");

                // Botão CURTIR
                html.append("<form method=\"POST\" action=\"/participar\">"); // O método POST é equivalente ao envio de dados ao banco de dados, ou seja, nesse caso ao clicar no botão o usuário está enviando informações ao BD
                html.append("<input type=\"hidden\" name=\"id\" value=\"").append(id).append("\">");
                html.append("<input type=\"hidden\" name=\"acao\" value=\"participar\">");
                html.append("<button type=\"submit\">Participar</button>");
                html.append("</form>");

                // Botão NÃO CURTIR
                html.append("<form method=\"POST\" action=\"/participar\">");
                html.append("<input type=\"hidden\" name=\"id\" value=\"").append(id).append("\">");
                html.append("<input type=\"hidden\" name=\"acao\" value=\"nao\">");
                html.append("<button type=\"submit\">Não participar</button>");
                html.append("</form>");


                html.append("</div>"); // A div é para organizar de forma visualmente agradável os cards
            }

            if (vazio) {
                html.append("<p>Nenhuma atividade cadastrada ainda.</p>"); // Se estiver vazio, mostre essa mensagem
            }

        } catch (SQLException e) { // Identifique um erro
            e.printStackTrace();
            html.append("<p>Erro ao carregar atividades.</p>");
        }

        html.append("</body></html>");

        // Enviar HTML gerado
        byte[] b = html.toString().getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, b.length);
        t.getResponseBody().write(b);
        t.close();
    }

    // ------------ ATIVIDADES PARA O PROFESSOR EXCLUIR

    private static void atividades(HttpExchange t) throws IOException {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>"); // Montando o esqueleto HTML para mostrar as curtidas
        html.append("<html><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<title>Aluno</title>");
        html.append("<link rel=\"stylesheet\" href=\"/style.css\">");
        html.append("</head><body>");
        html.append("<header class=\"cabecalho-topo\">");
        html.append("<h1>Bem-vindo(a) !</h1>");
        html.append("<a href=\"./login.html\" class=\"btn-voltar\">Voltar à página <img src=\"./src/assets/images/Login.svg\"></a>");
        html.append("</header>");
        html.append("<h1>Aluno</h1>");
        html.append("<p>As atividades disponíveis aparecem aqui:</p>");


        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, materia, descricao, data, participacao FROM dados ORDER BY id DESC")) { // para mostrar os dados obtidos

            boolean vazio = true; // Mostre os dados enquanto "vazio" realmente estiver vazio

            while (rs.next()) {
                vazio = false; // Enquanto não estiver vazio

                int id = rs.getInt("id"); // Extrata o valor da ID e mostre
                String nome = rs.getString("materia");
                String desc = rs.getString("descricao");
                String data = rs.getString("data");
                String participacao = rs.getString("participacao");

                // Classe extra para cor do card
                String classeExtra = "";
                if ("participacao".equals(participacao)) { // Se a postagem for curtida, mostre que o card foi curtido
                    classeExtra = "participar";
                } else if ("nao".equals(participacao)) {
                    classeExtra = "nao-participar";
                }

                html.append("<div class=\"card").append(classeExtra).append("\">");
                html.append("<p><strong>ID:</strong> ").append(id).append("</p>");
                html.append("<p><strong>Matéria:</strong> ").append(nome).append("</p>");
                html.append("<p><strong>Descrição:</strong> ").append(desc).append("</p>");
                html.append("<p><strong>Data:</strong> ").append(data).append("</p>");
                html.append("<p><strong>Participando:</strong> ").append(participacao).append("</p>");



                //Botão para Deletar (Mover função para a página de envio nas próximas versões)
                html.append("<form method=\"POST\" action=\"/deletar\">");
                html.append("<input type=\"hidden\" name=\"id\" value=\"").append(id).append("\">");
                html.append("<input type=\"hidden\" name=\"acao\" value=\"nao\">");
                html.append("<button type=\"submit\">Deletar</button>");
                html.append("</form>");

                html.append("</div>"); // A div é para organizar de forma visualmente agradável os cards
            }

            if (vazio) {
                html.append("<p>Nenhuma atividade cadastrada ainda.</p>"); // Se estiver vazio, mostre essa mensagem
            }

        } catch (SQLException e) { // Identifique um erro
            e.printStackTrace();
            html.append("<p>Erro ao carregar atividades.</p>");
        }

        html.append("</body></html>");

        // Enviar HTML gerado
        byte[] b = html.toString().getBytes(StandardCharsets.UTF_8);
        t.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, b.length);
        t.getResponseBody().write(b);
        t.close();
    }

    // -------------------- AVALIAR (curtir / não curtir um card específico) --------------------

    private static void participar(HttpExchange t) throws IOException {

        if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
            redirecionar(t, "/aluno");
            return;
        }

        String corpo = URLDecoder.decode(ler(t), StandardCharsets.UTF_8); // Decodificar
        String acao = pega(corpo, "acao"); // "curtir" ou "nao"
        String idStr = pega(corpo, "id");

        try {
            int id = Integer.parseInt(idStr);

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE dados SET participacao = ? WHERE id = ?")) {
                ps.setString(1, acao);
                ps.setInt(2, id);
                ps.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        redirecionar(t, "/aluno");
    }





    // -------------------- Funções auxiliares --------------------

    private static String pega(String corpo, String campo) {
        // corpo no formato: campo1=valor1&campo2=valor2...
        for (String s : corpo.split("&")) {
            String[] p = s.split("=");
            if (p.length == 2 && p[0].equals(campo)) return p[1];
        }
        return "";
    }

    private static String ler(HttpExchange t) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(t.getRequestBody(), StandardCharsets.UTF_8)
        );
        String linha = br.readLine();
        return (linha == null) ? "" : linha;
    }

    private static void enviar(HttpExchange t, String arq) throws IOException {
        File f = new File("src/main/java/" + arq);
        byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
        t.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        t.sendResponseHeaders(200, b.length);
        t.getResponseBody().write(b);
        t.close();
    }

    private static void enviarCSS(HttpExchange t, String arq) throws IOException {
        File f = new File("src/main/java/" + arq);
        byte[] b = java.nio.file.Files.readAllBytes(f.toPath());
        t.getResponseHeaders().add("Content-Type", "text/css; charset=UTF-8");
        t.sendResponseHeaders(200, b.length);
        t.getResponseBody().write(b);
        t.close();
    }

    private static void redirecionar(HttpExchange t, String rota) throws IOException {
        t.getResponseHeaders().add("Location", rota);
        t.sendResponseHeaders(302, -1);
        t.close();
    }
}
