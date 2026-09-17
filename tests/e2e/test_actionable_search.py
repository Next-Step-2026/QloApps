import re
from playwright.sync_api import Page, expect

def navigate_to_actionable_search(admin_page: Page, base_url: str):
    """Navega até a tela de Actionable Entity Search a partir do menu ou link do admin."""
    # Busca pelo link direto gerado pelo QloApps no menu lateral
    search_link = admin_page.locator("a[href*='controller=AdminActionableSearch']")
    
    if search_link.count() > 0:
        href = search_link.first.get_attribute("href")
        if href.startswith("http"):
            admin_page.goto(href)
        else:
            admin_page.goto(f"{base_url}/admin-dev/{href}")
    else:
        # Tenta clicar no menu de texto
        menu_item = admin_page.get_by_text("Actionable Entity Search", exact=False).first
        menu_item.click()

    admin_page.wait_for_load_state("domcontentloaded")
    expect(admin_page.locator("#search_query")).to_be_visible()

def test_actionable_search_page_renders(admin_page: Page, base_url: str):
    """Verifica se a tela do Actionable Entity Search abre e exibe o formulário de busca."""
    navigate_to_actionable_search(admin_page, base_url)

    # Valida título da seção e orientações
    expect(admin_page.locator(".panel-heading")).to_contain_text("Fast Lexical Search Engine")
    expect(admin_page.locator("#search_query")).to_be_visible()
    expect(admin_page.locator("button[name='submitSearchQuery']")).to_be_visible()

def test_actionable_search_suite_mar_two_adults(admin_page: Page, base_url: str):
    """
    Testa a feature buscando 'suite para 2 adultos com vista mar'.
    Valida a integração completa com o motor C++:
    - Extração de tokens normalizados ('suite', 'vista', 'mar')
    - Filtro de ocupação (2 adultos)
    - Comodidade (vista_mar)
    - Entidade casada: Suíte Master Vista Mar (room-suite-01)
    """
    navigate_to_actionable_search(admin_page, base_url)

    # 1. Preenche a consulta em linguagem natural
    query = "suite para 2 adultos com vista mar"
    admin_page.fill("#search_query", query)

    # 2. Clica em 'Search Entities'
    admin_page.click("button[name='submitSearchQuery']")
    admin_page.wait_for_load_state("domcontentloaded")

    # 3. Valida que a área de resultados (.well) foi renderizada
    results_box = admin_page.locator(".well")
    expect(results_box).to_be_visible()

    # 4. Valida os filtros extraídos e tokens reconhecidos pelo motor C++
    expect(results_box).to_contain_text("suite")
    expect(results_box).to_contain_text("2 Adult(s)")
    expect(results_box).to_contain_text("vista_mar")

    # 5. Valida que a Suíte Master foi encontrada e listada
    expect(results_box).to_contain_text("Suíte Master Vista Mar")
    expect(results_box).to_contain_text("room-suite-01")

def test_actionable_search_standard_casal(admin_page: Page, base_url: str):
    """Testa a busca de 'quarto standard casal' casando o quarto room-std-02."""
    navigate_to_actionable_search(admin_page, base_url)

    admin_page.fill("#search_query", "quarto standard casal com ar condicionado")
    admin_page.click("button[name='submitSearchQuery']")
    admin_page.wait_for_load_state("domcontentloaded")

    results_box = admin_page.locator(".well")
    expect(results_box).to_be_visible()
    expect(results_box).to_contain_text("Quarto Standard Casal")
    expect(results_box).to_contain_text("room-std-02")

def test_actionable_search_empty_query_validation(admin_page: Page, base_url: str):
    """Valida que uma query vazia é barrada pela validação do formulário."""
    navigate_to_actionable_search(admin_page, base_url)

    # Remove o atributo required para testar a validação do backend PHP
    admin_page.evaluate("document.getElementById('search_query').removeAttribute('required')")
    admin_page.fill("#search_query", "")
    admin_page.click("button[name='submitSearchQuery']")
    admin_page.wait_for_load_state("domcontentloaded")

    # Verifica alerta de erro amigável do módulo
    warning = admin_page.locator(".alert-warning")
    expect(warning).to_be_visible()
