param(
    [Parameter(Mandatory)] [string] $Servidor,
    [Parameter(Mandatory)] [string] $Chave,
    [string] $Backend
)
$ErrorActionPreference = 'Stop'

# No Windows PowerShell 5.1, $PSScriptRoot fica vazio dentro do param(); por isso o padrão é calculado aqui.
if (-not $Backend) { $Backend = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path) }

Write-Host '>> Gerando o .jar (sem testes, eles precisam do banco)'
Push-Location $Backend
try {
    & .\mvnw.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Falha no build do Maven' }
    $jar = Get-ChildItem target\*.jar | Where-Object { $_.Name -notlike '*plain*' } | Select-Object -First 1
} finally { Pop-Location }

Write-Host ">> Enviando $($jar.Name) para $Servidor"
# accept-new: aceita sozinho a chave do servidor na 1ª conexão (sem pergunta yes/no), mas recusa se ela mudar depois.
$opcoesSsh = @('-i', $Chave, '-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=15')
scp @opcoesSsh $jar.FullName "ubuntu@${Servidor}:/tmp/api.jar"
if ($LASTEXITCODE -ne 0) { throw 'Falha no envio (scp)' }

Write-Host '>> Instalando e reiniciando o serviço'
ssh @opcoesSsh "ubuntu@$Servidor" 'sudo mv /tmp/api.jar /opt/xepamais/api.jar && sudo chown xepamais:xepamais /opt/xepamais/api.jar && sudo systemctl restart xepamais-api && sleep 25 && sudo systemctl --no-pager status xepamais-api | head -5'

Write-Host ">> Teste: http://${Servidor}:8080/api/auth/login (deve responder, mesmo que com erro 4xx)"
